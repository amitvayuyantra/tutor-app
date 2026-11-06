@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tutordiary.app.R
import com.tutordiary.app.ui.attendance.AttendanceScreen
import com.tutordiary.app.ui.batches.BatchEditScreen
import com.tutordiary.app.ui.batches.BatchesScreen
import com.tutordiary.app.ui.home.HomeScreen
import com.tutordiary.app.ui.settings.SettingsScreen
import com.tutordiary.app.ui.settings.TemplatesScreen
import com.tutordiary.app.ui.students.PaymentEditScreen
import com.tutordiary.app.ui.students.StudentDetailScreen
import com.tutordiary.app.ui.students.StudentEditScreen
import com.tutordiary.app.ui.students.StudentsScreen
import com.tutordiary.app.ui.students.WaiverScreen

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Filled.Home),
    STUDENTS("students", R.string.tab_students, Icons.Filled.Person),
    ATTENDANCE("attendance", R.string.tab_attendance, Icons.Filled.CheckCircle),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private object Routes {
    const val STUDENT = "student/{studentId}"
    const val STUDENT_EDIT = "student_edit/{studentId}"
    const val PAYMENT = "payment/{studentId}/{paymentId}"
    const val BATCHES = "batches"
    const val TEMPLATES = "templates"
    const val WAIVER = "waiver/{studentId}"
    const val BATCH_EDIT = "batch_edit/{batchId}"
    fun waiver(studentId: Long) = "waiver/$studentId"
    const val ACCESS = "access/{studentId}"
    fun access(studentId: Long) = "access/$studentId"
    fun batchEdit(id: Long) = "batch_edit/$id"
    fun student(id: Long) = "student/$id"
    fun studentEdit(id: Long) = "student_edit/$id"
    fun payment(studentId: Long, paymentId: Long = 0) = "payment/$studentId/$paymentId"
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav(onSignOut: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        val bottom = PaddingValues(bottom = inner.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
        ) {
            val idArg = navArgument("studentId") { type = NavType.LongType }
            val back: () -> Unit = { nav.popBackStack() }

            composable(Tab.HOME.route) {
                HomeScreen(
                    onOpenStudent = { nav.navigate(Routes.student(it)) },
                    onRecordPayment = { nav.navigate(Routes.payment(it)) },
                    onAddStudent = { nav.navigate(Routes.studentEdit(0)) },
                    onOpenSettings = { nav.switchTab(Tab.SETTINGS.route) },
                )
            }
            composable(Tab.STUDENTS.route) {
                StudentsScreen(
                    onOpenStudent = { nav.navigate(Routes.student(it)) },
                    onAddStudent = { nav.navigate(Routes.studentEdit(0)) },
                    onOpenBatches = { nav.navigate(Routes.BATCHES) },
                )
            }
            composable(Tab.ATTENDANCE.route) {
                AttendanceScreen(onOpenBatches = { nav.navigate(Routes.BATCHES) })
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    onOpenTemplates = { nav.navigate(Routes.TEMPLATES) },
                    onOpenBatches = { nav.navigate(Routes.BATCHES) },
                    onSignOut = onSignOut,
                )
            }
            composable(Routes.STUDENT, arguments = listOf(idArg)) {
                StudentDetailScreen(
                    onBack = back,
                    onEdit = { nav.navigate(Routes.studentEdit(it)) },
                    onRecordPayment = { nav.navigate(Routes.payment(it)) },
                    onEditPayment = { s, p -> nav.navigate(Routes.payment(s, p)) },
                    onWaive = { nav.navigate(Routes.waiver(it)) },
                    onOpenAccess = { nav.navigate(Routes.access(it)) },
                )
            }
            composable(Routes.STUDENT_EDIT, arguments = listOf(idArg)) {
                StudentEditScreen(onDone = back, onBack = back)
            }
            composable(
                Routes.PAYMENT,
                arguments = listOf(idArg, navArgument("paymentId") { type = NavType.LongType }),
            ) {
                PaymentEditScreen(onDone = back)
            }
            composable(Routes.BATCHES) { BatchesScreen(onBack = back, onEditBatch = { nav.navigate(Routes.batchEdit(it)) }) }
            composable(Routes.BATCH_EDIT, arguments = listOf(navArgument("batchId") { type = NavType.LongType })) {
                BatchEditScreen(onDone = back)
            }
            composable(Routes.WAIVER, arguments = listOf(idArg)) { WaiverScreen(onDone = back) }
            composable(Routes.ACCESS, arguments = listOf(idArg)) { com.tutordiary.app.ui.students.AccessScreen(onBack = back) }
            composable(Routes.TEMPLATES) { TemplatesScreen(onBack = back) }
        }
    }
}
