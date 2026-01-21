package com.github.artificyal.projecttabs

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JButton
import com.intellij.openapi.diagnostic.Logger
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.ScrollPaneConstants
import javax.swing.JLabel
import java.util.concurrent.ConcurrentHashMap


import java.util.Collections
import java.util.WeakHashMap
import com.intellij.openapi.application.ApplicationManager

class ProjectTabsFactory() : IdeRootPaneNorthExtension {

    private val LOG = Logger.getInstance(ProjectTabsFactory::class.java)

    companion object {
        const val EXTENSION_ID = "com.github.artificyal.projecttabs.ProjectTabsFactory"
        private val PANELS = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ProjectTabsPanel, Boolean>()))
        var singleWindowMode = true

        fun refreshAll() {
            ApplicationManager.getApplication().invokeLater {
                synchronized(PANELS) {
                    val iterator = PANELS.iterator()
                    while (iterator.hasNext()) {
                        try {
                            iterator.next().refreshTabs()
                        } catch (e: Exception) {
                            // Ignore if panel is disposed
                        }
                    }
                }
            }
        }

        fun ensureSingleVisible(activeProject: Project) {
            ApplicationManager.getApplication().invokeLater {
                val activeFrame = WindowManager.getInstance().getFrame(activeProject)
                if (activeFrame == null) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(500)
                        ApplicationManager.getApplication().invokeLater {
                            val retryFrame = WindowManager.getInstance().getFrame(activeProject)
                            if (retryFrame != null) {
                                performEnsureSingleVisible(activeProject, retryFrame)
                            }
                        }
                    }
                } else {
                    performEnsureSingleVisible(activeProject, activeFrame)
                }
            }
        }

        private fun performEnsureSingleVisible(activeProject: Project, activeFrame: JFrame) {
            val openProjects = ProjectManager.getInstance().openProjects

            val currentFocusedFrame = openProjects
                .filter { it != activeProject }
                .mapNotNull { WindowManager.getInstance().getFrame(it) }
                .find { it.isFocused || it.isActive }

            if (currentFocusedFrame != null) {
                activeFrame.bounds = currentFocusedFrame.bounds
                if (currentFocusedFrame.extendedState != JFrame.ICONIFIED) {
                    activeFrame.extendedState = currentFocusedFrame.extendedState
                }
            }

            activeFrame.isVisible = true
            activeFrame.toFront()
            activeFrame.requestFocus()


            refreshAll()
        }
    }

    override val key: String = EXTENSION_ID

    override fun createComponent(project: Project, isDocked: Boolean): JComponent? {
        LOG.info("ProjectTabsFactory.createComponent() for ${project.name}")
        val panel = ProjectTabsPanel(project)
        PANELS.add(panel)
        
        val scrollPane = JBScrollPane(panel).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(0, 30)
        }
        panel.refreshTabs()
        return scrollPane
    }

    private class ProjectTabsPanel(private val project: Project) : JBPanel<ProjectTabsPanel>() {

        init {
            isOpaque = true
            background = com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
            layout = java.awt.GridLayout(1, 0)
            border = JBUI.Borders.customLine(com.intellij.ui.JBColor(0x393b40, 0x393b40), 0, 0, 1, 0)
        }

        fun refreshTabs() {
            val openProjects = ProjectManager.getInstance().openProjects
            if (componentCount == openProjects.size && componentCount > 0) {
                updateTabsUI(openProjects)
            } else {
                buildAllTabs(openProjects)
            }
        }

        private fun updateTabsUI(openProjects: Array<Project>) {
            for (i in openProjects.indices) {
                val openProject = openProjects[i]
                val isSelected = openProject == project

                val tabContainer = getComponent(i) as? JBPanel<*> ?: continue
                val nameLabel = tabContainer.getComponent(0) as? JLabel

                tabContainer.background = if (isSelected) com.intellij.ui.JBColor(0x35373A, 0x35373A) else com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)

                nameLabel?.apply {
                    foreground = com.intellij.ui.JBColor(0x909090, 0x909090)
                    font = if (isSelected) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(12f)
                }
            }
        }

        private fun buildAllTabs(openProjects: Array<Project>) {
            removeAll()
            layout = java.awt.GridLayout(1, openProjects.size)

            for (openProject in openProjects) {
                val isSelected = openProject == project

                val tabContainer = JBPanel<JBPanel<*>>(java.awt.BorderLayout()).apply {
                    isOpaque = true
                    background = if (isSelected) com.intellij.ui.JBColor(0x35373A, 0x35373A) else com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                    border = JBUI.Borders.customLine(com.intellij.ui.JBColor(0x393b40, 0x393b40), 0, 0, 0, 1)

                    val nameLabel = JLabel(openProject.name, javax.swing.SwingConstants.CENTER).apply {
                        foreground = com.intellij.ui.JBColor(0x909090, 0x909090)
                        font = if (isSelected) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(12f)
                    }
                    add(nameLabel, java.awt.BorderLayout.CENTER)
                }

                tabContainer.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                        if (!isSelected) {
                            tabContainer.background = com.intellij.ui.JBColor(0x2B2D30, 0x2B2D30)
                            tabContainer.repaint()
                        }
                    }

                    override fun mouseExited(e: java.awt.event.MouseEvent?) {
                        if (!isSelected) {
                            tabContainer.background = com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                            tabContainer.repaint()
                        }
                    }

                    override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        if (openProject != project) {
                            ProjectTabsFactory.ensureSingleVisible(openProject)
                        }
                    }
                })

                add(tabContainer)
            }
            revalidate()
            repaint()
        }
    }

    private fun focusProject(targetProject: Project) {
        ProjectTabsFactory.ensureSingleVisible(targetProject)
    }
}
