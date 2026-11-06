package com.nuvio.tv.go3

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Only for explicitly selected offline fixture UI tests: never initialize Nuvio authentication/sync. */
class Go3FixtureRunner: AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader,className: String,context: Context): Application =
        super.newApplication(cl,Application::class.java.name,context)
}
