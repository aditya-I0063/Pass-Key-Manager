package com.bhardwaj.passkey

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.model.Preview
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.presentation.screens.preview_screen.PreviewEffect
import com.bhardwaj.passkey.presentation.screens.preview_screen.PreviewIntent
import com.bhardwaj.passkey.presentation.screens.preview_screen.PreviewViewModel
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

/**
 * Reducer tests for the reference MVI screen.
 *
 * These are plain JVM tests: nothing here touches Android, because effects carry unresolved
 * UiText rather than resolved strings, and the state is an ordinary data class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

    // Unconfined rather than Standard: these assert on derived state after dispatching an
    // intent, and with a standard dispatcher nothing has run by the time the assertion executes.
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakePasskeyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePasskeyRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PreviewViewModel(repository, SavedStateHandle())

    private fun preview(id: Long, heading: String, sequence: Long = 0) =
        Preview(id = id, heading = heading, category = Category.BANKS, sequence = sequence)

    @Test
    fun `swiping to delete hides the row but writes nothing`() = runTest(dispatcher) {
        // The regression this release exists for: the old implementation deleted immediately and
        // re-inserted on cancel, so a process death while the dialog was open lost the entry.
        repository.seedPreviews(preview(1, "Bank"), preview(2, "Mail"))
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(PreviewIntent.SwipedToDelete(preview(1, "Bank")))
            val state = expectMostRecentItem()

            assertThat(state.pendingDelete?.id).isEqualTo(1L)
            assertThat(state.items.map { it.heading }).containsExactly("Mail")
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.writeCount).isEqualTo(0)
        assertThat(repository.currentPreviews()).hasSize(2)
    }

    @Test
    fun `cancelling a delete restores the row and still writes nothing`() = runTest(dispatcher) {
        repository.seedPreviews(preview(1, "Bank"))
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(PreviewIntent.SwipedToDelete(preview(1, "Bank")))
            vm.onIntent(PreviewIntent.DeleteCancelled)
            val state = expectMostRecentItem()

            assertThat(state.pendingDelete).isNull()
            assertThat(state.items.map { it.heading }).containsExactly("Bank")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.writeCount).isEqualTo(0)
    }

    @Test
    fun `confirming a delete removes the entry and its details`() = runTest(dispatcher) {
        repository.seedPreviews(preview(1, "Bank"))
        repository.createDetail(previewId = 1, question = "Password", answer = "secret")
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(PreviewIntent.SwipedToDelete(preview(1, "Bank")))
            vm.onIntent(PreviewIntent.DeleteConfirmed)
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.currentPreviews()).isEmpty()
        // Orphaned details would otherwise linger: there is no foreign key cascade.
        assertThat(repository.currentDetails()).isEmpty()
    }

    @Test
    fun `saving an edit without renaming does not report a duplicate`() = runTest(dispatcher) {
        // The lookup finds the very row being edited; treating that as a clash used to report
        // "heading exists" and silently discard the edit.
        repository.seedPreviews(preview(1, "Bank"))
        val vm = viewModel()

        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(PreviewIntent.EditClicked(preview(1, "Bank")))
            vm.onIntent(PreviewIntent.SaveClicked)
            expectNoEvents()
        }
        assertThat(repository.currentPreviews().single().heading).isEqualTo("Bank")
    }

    @Test
    fun `saving a heading that clashes with another row is rejected`() = runTest(dispatcher) {
        repository.seedPreviews(preview(1, "Bank"), preview(2, "Mail"))
        val vm = viewModel()

        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(PreviewIntent.EditClicked(preview(2, "Mail")))
            vm.onIntent(PreviewIntent.HeadingChanged("Bank"))
            vm.onIntent(PreviewIntent.SaveClicked)

            val effect = awaitItem()
            assertThat(effect).isEqualTo(
                PreviewEffect.ShowSnackbar(UiText.StringResource(R.string.heading_exists))
            )
        }
    }

    @Test
    fun `saving a blank heading is rejected without writing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(PreviewIntent.AddClicked)
            vm.onIntent(PreviewIntent.HeadingChanged("   "))
            vm.onIntent(PreviewIntent.SaveClicked)

            assertThat(awaitItem()).isEqualTo(
                PreviewEffect.ShowSnackbar(UiText.StringResource(R.string.enter_valid_heading))
            )
        }
        assertThat(repository.writeCount).isEqualTo(0)
    }

    @Test
    fun `adding trims the heading`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            awaitItem()
            vm.onIntent(PreviewIntent.AddClicked)
            vm.onIntent(PreviewIntent.HeadingChanged("  Bank  "))
            vm.onIntent(PreviewIntent.SaveClicked)
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.currentPreviews().single().heading).isEqualTo("Bank")
    }

    @Test
    fun `query filters without touching the database`() = runTest(dispatcher) {
        repository.seedPreviews(preview(1, "Bank"), preview(2, "Mail"))
        val vm = viewModel()

        vm.state.test {
            awaitItem()
            vm.onIntent(PreviewIntent.QueryChanged("ban"))
            assertThat(expectMostRecentItem().items.map { it.heading }).containsExactly("Bank")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.writeCount).isEqualTo(0)
    }

    @Test
    fun `reordering renumbers sequences within the category`() = runTest(dispatcher) {
        repository.seedPreviews(
            preview(1, "A", sequence = 0),
            preview(2, "B", sequence = 1),
            preview(3, "C", sequence = 2)
        )
        val vm = viewModel()

        vm.state.test {
            // The state flow is WhileSubscribed, so it needs a live collector before the
            // reorder can read the current ordering.
            awaitItem()
            vm.onIntent(PreviewIntent.Moved(fromIndex = 0, toIndex = 2))
            cancelAndIgnoreRemainingEvents()
        }

        val ordered = repository.currentPreviews().sortedBy { it.sequence }.map { it.heading }
        assertThat(ordered).containsExactly("B", "C", "A").inOrder()
        assertThat(repository.currentPreviews().map { it.sequence }.sorted())
            .containsExactly(0L, 1L, 2L).inOrder()
    }

    @Test
    fun `tapping an entry navigates with its id`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(PreviewIntent.ItemClicked(previewId = 7))
            assertThat(awaitItem()).isEqualTo(PreviewEffect.Navigate(NavRoute.Details(7)))
        }
    }

    @Test
    fun `long pressing a heading copies it as non-sensitive`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onIntent(PreviewIntent.ItemLongPressed("Bank"))
            // A heading is not a secret, so it must not be marked sensitive or auto-cleared.
            assertThat(awaitItem())
                .isEqualTo(PreviewEffect.CopyToClipboard("Bank", isSensitive = false))
        }
    }
}
