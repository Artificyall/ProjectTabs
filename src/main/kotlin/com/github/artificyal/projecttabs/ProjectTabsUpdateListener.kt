package com.github.artificyal.projecttabs

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.application.ApplicationManager
import javax.swing.JFrame


class ProjectTabsUpdateListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        if (ProjectTabsFactory.singleWindowMode) {
            ProjectTabsFactory.ensureSingleVisible(project)
        }
        ProjectTabsFactory.refreshAll()
    }

    override fun projectClosed(project: Project) {
        if (ProjectTabsFactory.singleWindowMode) {
            ApplicationManager.getApplication().invokeLater {
                val openProjects = ProjectManager.getInstance().openProjects
                if (openProjects.isNotEmpty()) {
                    val anyVisible = openProjects.any { 
                        WindowManager.getInstance().getFrame(it)?.isVisible == true 
                    }
                    if (!anyVisible) {
                        ProjectTabsFactory.ensureSingleVisible(openProjects[0])
                    }
                }
            }
        }
        ProjectTabsFactory.refreshAll()
    }
}



