package com.github.artificyal.projecttabs

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.WindowManager


class ProjectTabsUpdateListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        ProjectTabsFactory.refreshAll()
    }

    override fun projectClosed(project: Project) {
        ProjectTabsFactory.refreshAll()
    }
}



