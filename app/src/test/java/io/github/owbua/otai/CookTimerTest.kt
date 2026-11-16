package io.github.owbua.otai

import io.github.owbua.otai.data.ConflictMode
import io.github.owbua.otai.data.RecipeRepository
import io.github.owbua.otai.data.RecipeStore
import io.github.owbua.otai.io.ImportedRecipe
import io.github.owbua.otai.ui.cook.CookViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CookTimerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `Timer laeuft pausiert und meldet Ablauf genau einmal`() = runTest(dispatcher) {
        val repo = RecipeRepository(RecipeStore(tmp.newFolder()), backgroundScope, dispatcher)
        repo.import(listOf(ImportedRecipe(TestData.hackPilz, null, "x")), ConflictMode.COPY)
        val finished = mutableListOf<String>()
        val vm = CookViewModel(
            repo, "hack-pilz-pfanne", 2,
            clock = { testScheduler.currentTime },
            onTimerFinished = { _, label -> finished += label },
        )
        runCurrent()
        assertEquals(4, vm.state.value.recipe?.steps?.size)

        vm.setStep(2) // 3-Minuten-Timer
        vm.toggleTimer(2)
        advanceTimeBy(60_000); runCurrent()
        val t1 = vm.state.value.timers[2]!!
        assertTrue(t1.running)
        assertTrue(kotlin.math.abs(120_000 - t1.remainingAt(vm.state.value.now)) < 300)

        vm.toggleTimer(2) // Pause
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertFalse(vm.state.value.timers[2]!!.running)
        assertTrue(kotlin.math.abs(120_000 - vm.state.value.timers[2]!!.remainingMs) < 300)

        // parallel: Kartoffel-Timer aus Schritt 1
        vm.toggleTimer(0)
        vm.toggleTimer(2)
        advanceTimeBy(121_000); runCurrent()
        assertEquals(listOf("Zwiebeln und Pilze mitbraten"), finished)
        assertEquals(0, vm.state.value.timers[2]!!.remainingMs)
        assertTrue("Kartoffel-Timer läuft weiter", vm.state.value.timers[0]!!.running)

        advanceTimeBy(20 * 60_000L); runCurrent()
        assertEquals(listOf("Zwiebeln und Pilze mitbraten", "Kartoffeln kochen"), finished)

        vm.resetTimer(0); runCurrent()
        assertEquals(1_200_000L, vm.state.value.timers[0]!!.remainingMs)
    }
}
