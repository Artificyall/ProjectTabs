package com.github.artificyal.projecttabs

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class ProjectTabsStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        if (ProjectTabsFactory.singleWindowMode) {
            ProjectTabsFactory.ensureSingleVisible(project)
        }
        ProjectTabsFactory.refreshAll()
    }
}

