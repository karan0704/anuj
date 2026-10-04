package com.karan.anuj.feature.place

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.karan.anuj.feature.place.platform.PhoneLocationSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacePlatformTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val source = PhoneLocationSource(app)

    @Test
    fun `without the permission nothing is read and nothing is asked of the phone`() = runTest {
        assertFalse(source.permitted)
        assertNull(source.current())
    }

    @Test
    fun `location while the app is closed needs its own permission`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(source.permitted)
        assertFalse(source.permittedInBackground)

        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(source.permittedInBackground)
    }
}
