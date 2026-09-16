package com.bhardwaj.passkey

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.PasswordCharacterClass
import com.bhardwaj.passkey.domain.totp.Base32
import com.bhardwaj.passkey.domain.totp.TotpAlgorithm
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailEffect
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailIntent
import com.bhardwaj.passkey.presentation.screens.detail_screen.DetailViewModel
import com.bhardwaj.passkey.utils.UiText
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric, unlike every other unit test in this module.
 *
 * SavedStateHandle.toRoute builds an android.os.Bundle internally to decode the typed route
 * argument, which plain JVM tests cannot do. Making the production code read the argument by
 * string name instead would have suited the test at the cost of the type safety this release
 * introduced, so the test carries the weight rather than the ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
// A stock Application, not PasskeyApplication: the real one loads the SQLCipher native
// library in onCreate, which does not exist on the JVM. This test needs Android types, not
// the app's runtime.
@Config(sdk = [34], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakePasskeyRepository

    private companion object {
        const val PREVIEW_ID = 42L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePasskeyRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The ViewModel decodes its argument with `toRoute`, which reads the route's serialized
     * arguments out of the handle under the destination's own key.
     */
    private fun viewModel() = DetailViewModel(
        repository,
        SavedStateHandle(mapOf("previewId" to PREVIEW_ID))
    )

    private suspend fun seedDetail(question: String, answer: String): Long =
        repository.createDetail(previewId = PREVIEW_ID, question = question, answer = answer)

    @Test
    fun `swiping to delete hides the row but writes nothing`() = runTest(dispatcher) {
        seedDetail("Password", "secret")
        val writesAfterSeed = repository.writeCount
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            val row = repository.currentDetails().single()
            vm.onIntent(DetailIntent.SwipedToDelete(row))
            val state = expectMostRecentItem()

            assertThat(state.pendingDelete?.id).isEqualTo(row.id)
            assertThat(state.items).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.writeCount).isEqualTo(writesAfterSeed)
        assertThat(repository.currentDetails()).hasSize(1)
    }

    @Test
    fun `cancelling a delete restores the row`() = runTest(dispatcher) {
        seedDetail("Password", "secret")
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            val row = repository.currentDetails().single()
            vm.onIntent(DetailIntent.SwipedToDelete(row))
            vm.onIntent(DetailIntent.DeleteCancelled)
            assertThat(expectMostRecentItem().items).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.currentDetails()).hasSize(1)
    }

    @Test
    fun `both create and edit trim the answer`() = runTest(dispatcher) {
        // Only the create path trimmed before, so a trailing space crept into stored passwords
        // on edit and then failed silently wherever it was pasted.
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.AddClicked)
            vm.onIntent(DetailIntent.QuestionChanged("  Password  "))
            vm.onIntent(DetailIntent.AnswerChanged("  s3cret  "))
            vm.onIntent(DetailIntent.SaveClicked)
            expectMostRecentItem()

            val created = repository.currentDetails().single()
            assertThat(created.question).isEqualTo("Password")
            assertThat(created.answer).isEqualTo("s3cret")

            vm.onIntent(DetailIntent.EditClicked(created))
            vm.onIntent(DetailIntent.AnswerChanged("  changed  "))
            vm.onIntent(DetailIntent.SaveClicked)
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.currentDetails().single().answer).isEqualTo("changed")
    }

    @Test
    fun `a blank question or answer is rejected without writing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(DetailIntent.AddClicked)
            vm.onIntent(DetailIntent.QuestionChanged("Password"))
            vm.onIntent(DetailIntent.AnswerChanged("   "))
            vm.onIntent(DetailIntent.SaveClicked)

            assertThat(awaitItem()).isEqualTo(
                DetailEffect.ShowSnackbar(
                    UiText.StringResource(R.string.enter_valid_title_n_response)
                )
            )
        }
        assertThat(repository.currentDetails()).isEmpty()
    }

    @Test
    fun `a generated answer is stored as a secret`() = runTest(dispatcher) {
        // isSecret is what makes the analyser correct outside English, so the generator marking
        // its own output matters.
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.AddClicked)
            vm.onIntent(DetailIntent.QuestionChanged("Password"))
            vm.onIntent(DetailIntent.GenerateClicked)
            vm.onIntent(DetailIntent.SaveClicked)
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        val created = repository.currentDetails().single()
        assertThat(created.isSecret).isTrue()
        assertThat(created.answer).isNotEmpty()
    }

    @Test
    fun `typing over a generated answer clears the generated flag`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.AddClicked)
            vm.onIntent(DetailIntent.QuestionChanged("Note"))
            vm.onIntent(DetailIntent.GenerateClicked)
            vm.onIntent(DetailIntent.AnswerChanged("typed by hand"))
            vm.onIntent(DetailIntent.SaveClicked)
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.currentDetails().single().isSecret).isFalse()
    }

    @Test
    fun `turning off the last character class falls back to lowercase`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.PolicyClicked)
            PasswordCharacterClass.entries.forEach {
                vm.onIntent(DetailIntent.CharacterClassToggled(it, enabled = false))
            }
            val policy = expectMostRecentItem().policy
            // An empty pool would be unrepresentable state; the UI can never show all switches off.
            assertThat(policy.classes).containsExactly(PasswordCharacterClass.LOWERCASE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `length is clamped to the supported range`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.LengthChanged(999))
            assertThat(expectMostRecentItem().policy.length).isEqualTo(32)
            vm.onIntent(DetailIntent.LengthChanged(1))
            assertThat(expectMostRecentItem().policy.length).isEqualTo(4)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `long pressing an answer copies it as sensitive`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(DetailIntent.LongPressed("s3cret"))
            // Unlike an entry heading, an answer is a secret.
            assertThat(awaitItem())
                .isEqualTo(DetailEffect.CopyToClipboard("s3cret", isSensitive = true))
        }
    }

    @Test
    fun `query matches both question and answer`() = runTest(dispatcher) {
        seedDetail("Username", "alice")
        seedDetail("Password", "s3cret")
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.QueryChanged("alice"))
            assertThat(expectMostRecentItem().items.map { it.question })
                .containsExactly("Username")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only details for this entry are shown`() = runTest(dispatcher) {
        seedDetail("Mine", "a")
        repository.createDetail(previewId = 999, question = "Someone else", answer = "b")
        val vm = viewModel()

        vm.state.test {
            // Seeded before the ViewModel existed, so the first emission already carries them;
            // there is no later emission to wait for.
            assertThat(awaitItem().items.map { it.question }).containsExactly("Mine")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reordering renumbers sequences in one transaction`() = runTest(dispatcher) {
        seedDetail("A", "1"); seedDetail("B", "2"); seedDetail("C", "3")
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.Moved(fromIndex = 0, toIndex = 2))
            cancelAndIgnoreRemainingEvents()
        }

        val ordered: List<Detail> = repository.currentDetails().sortedBy { it.sequence }
        assertThat(ordered.map { it.question }).containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun `an otpauth link becomes an authenticator with its own parameters`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.AddAuthenticatorClicked)
            vm.onIntent(
                DetailIntent.AuthenticatorInputChanged(
                    "otpauth://totp/GitHub:alice?secret=GEZDGNBVGY3TQOJQ&algorithm=SHA256&digits=8"
                )
            )
            vm.onIntent(DetailIntent.AuthenticatorSaveClicked)
            assertThat(expectMostRecentItem().totpEditor).isNull()
            cancelAndIgnoreRemainingEvents()
        }

        val entry = repository.currentTotps().single()
        assertThat(entry.label).isEqualTo("GitHub")
        assertThat(entry.config.algorithm).isEqualTo(TotpAlgorithm.SHA256)
        assertThat(entry.config.digits).isEqualTo(8)
        assertThat(entry.config.secret).isEqualTo(Base32.decode("GEZDGNBVGY3TQOJQ"))
    }

    @Test
    fun `a bare secret typed by hand is accepted too`() = runTest(dispatcher) {
        // Every setup page that shows a QR code also prints this, which is why the app needs no
        // camera permission to add an authenticator.
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.AddAuthenticatorClicked)
            vm.onIntent(DetailIntent.AuthenticatorInputChanged("gezd gnbv gy3t qojq"))
            vm.onIntent(DetailIntent.AuthenticatorSaveClicked)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.currentTotps()).hasSize(1)
    }

    @Test
    fun `invalid input marks the dialog rather than storing an unusable secret`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.state.test {
                awaitItem()
                vm.onIntent(DetailIntent.AddAuthenticatorClicked)
                vm.onIntent(DetailIntent.AuthenticatorInputChanged("not a key!"))
                vm.onIntent(DetailIntent.AuthenticatorSaveClicked)

                val editor = expectMostRecentItem().totpEditor
                assertThat(editor).isNotNull()
                assertThat(editor!!.isInvalid).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(repository.currentTotps()).isEmpty()
        }

    @Test
    fun `typing again clears the error so the user is not told off while correcting it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.state.test {
                awaitItem()
                vm.onIntent(DetailIntent.AddAuthenticatorClicked)
                vm.onIntent(DetailIntent.AuthenticatorInputChanged("nope!"))
                vm.onIntent(DetailIntent.AuthenticatorSaveClicked)
                vm.onIntent(DetailIntent.AuthenticatorInputChanged("GEZDGNBVGY3TQOJQ"))
                assertThat(expectMostRecentItem().totpEditor?.isInvalid).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `copying a code marks it sensitive`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(DetailIntent.AuthenticatorCodeCopied("123456"))
            assertThat(awaitItem())
                .isEqualTo(DetailEffect.CopyToClipboard("123456", isSensitive = true))
        }
    }

    @Test
    fun `editing a secret records what it used to be`() = runTest(dispatcher) {
        val id = repository.createDetail(
            previewId = PREVIEW_ID, question = "Password", answer = "old", isSecret = true
        )
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            val row = repository.currentDetails().single { it.id == id }
            vm.onIntent(DetailIntent.EditClicked(row))
            vm.onIntent(DetailIntent.AnswerChanged("new"))
            vm.onIntent(DetailIntent.SaveClicked)
            expectMostRecentItem()

            vm.onIntent(DetailIntent.HistoryClicked(repository.currentDetails().single()))
            val history = expectMostRecentItem().history
            assertThat(history).isNotNull()
            assertThat(history!!.entries.map { it.answer }).containsExactly("old")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a non-secret detail records no history`() = runTest(dispatcher) {
        // Usernames and notes change without consequence; keeping every version of one is
        // storing plaintext nobody asked to keep.
        val id = repository.createDetail(
            previewId = PREVIEW_ID, question = "Username", answer = "alice"
        )
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(DetailIntent.EditClicked(repository.currentDetails().single { it.id == id }))
            vm.onIntent(DetailIntent.AnswerChanged("bob"))
            vm.onIntent(DetailIntent.SaveClicked)
            expectMostRecentItem()

            vm.onIntent(DetailIntent.HistoryClicked(repository.currentDetails().single()))
            assertThat(expectMostRecentItem().history?.entries).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
