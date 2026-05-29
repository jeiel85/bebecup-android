package com.bebecup.app

import com.bebecup.app.domain.usecase.CompareParentPickWithAiPickUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareParentPickWithAiPickTest {

    private val useCase = CompareParentPickWithAiPickUseCase()

    @Test
    fun `matched when parent winner equals AI top pick`() {
        val result = useCase(parentWinnerId = 5, aiTopPhotoId = 5)
        assertTrue(result!!.matched)
        assertEquals(5, result.aiTopPhotoId)
    }

    @Test
    fun `not matched when picks differ`() {
        val result = useCase(parentWinnerId = 5, aiTopPhotoId = 9)
        assertFalse(result!!.matched)
    }

    @Test
    fun `null when tournament was not AI-sourced`() {
        assertNull(useCase(parentWinnerId = 5, aiTopPhotoId = null))
    }
}
