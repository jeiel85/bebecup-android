package com.bebecup.app

import android.content.Context
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.tournament.TournamentEngine
import com.bebecup.app.domain.tournament.TournamentSnapshots
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** An unfinished world cup comes back after Android ends the app in the background. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorldCupRestoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        BebecupPrefs(context).onboardingSeen = true
    }

    @After
    fun tearDown() {
        Thread { AppDatabase.getDatabase(context).clearAllTables() }.apply { start(); join() }
    }

    private fun seedPhotos(count: Int): List<Int> = runBlocking {
        val dao = AppDatabase.getDatabase(context).babyPhotoDao()
        val ids = (0 until count).map { i ->
            dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = "2026-05-0${i + 1}")).toInt()
        }
        dao.markBest(ids, 1_000L)
        ids
    }

    /** Room answers on its own threads, then the view model continues on the main looper. */
    private fun waitUntil(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition never became true")
    }

    private fun savedBracket(handle: SavedStateHandle) = TournamentSnapshots.decode(handle.get<IntArray>("tournament"))

    @Test
    fun `an unfinished bracket resumes on the match the family left`() {
        val ids = seedPhotos(4)
        // Second match of the first round: the first was already won by ids[0].
        val handle = SavedStateHandle(mapOf("tournament" to TournamentSnapshots.encode(TournamentEngine.Snapshot(4, ids, listOf(ids[0]), 1, 0))))

        val viewModel = BabyCupViewModel(ApplicationProvider.getApplicationContext(), handle)
        waitUntil { viewModel.currentScreen == UiScreen.WorldCupPlay }

        val match = viewModel.currentMatch!!
        assertEquals(listOf(ids[2], ids[3]), listOf(match.left.id, match.right.id))
        assertEquals("2 / 2", viewModel.currentMatchLabel)

        // A vote is saved right away, so the next restore starts at the final.
        viewModel.vote(match, match.left)
        assertEquals(listOf(ids[0], ids[2]), savedBracket(handle)!!.pool)

        // Leaving the play screen abandons the bracket.
        viewModel.navigateBack()
        assertNull(handle.get<IntArray>("tournament"))
    }

    @Test
    fun `a bracket whose photo was deleted is forgotten`() {
        val ids = seedPhotos(2)
        val handle = SavedStateHandle(mapOf("tournament" to TournamentSnapshots.encode(TournamentEngine.Snapshot(2, listOf(ids[0], 999), emptyList(), 0, 0))))

        val viewModel = BabyCupViewModel(ApplicationProvider.getApplicationContext(), handle)
        waitUntil { handle.get<IntArray>("tournament") == null }

        assertEquals(UiScreen.Dashboard, viewModel.currentScreen)
    }
}
