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

    private class ProjectTabsPanel(private val project: Project) : JBPanel<ProjectTabsPanel>(FlowLayout(FlowLayout.LEFT, 0, 0)) {
        
        init {
            isOpaque = true
            background = JBUI.CurrentTheme.DefaultTabs.background()
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.DefaultTabs.borderColor(), 0, 0, 1, 0)
        }

        fun refreshTabs() {
            removeAll()
            val openProjects = ProjectManager.getInstance().openProjects
            for (openProject in openProjects) {
                val isSelected = openProject == project
                val button = JButton(openProject.name).apply {
                    isFocusable = false
                    isContentAreaFilled = isSelected
                    isBorderPainted = false
                    margin = JBUI.insets(2, 10)
                    
                    if (isSelected) {
                        background = JBUI.CurrentTheme.DefaultTabs.underlineColor()
                        foreground = JBUI.CurrentTheme.DefaultTabs.underlinedTabForeground()
                    } else {
                        foreground = JBUI.CurrentTheme.Label.foreground()
                        background = JBUI.CurrentTheme.DefaultTabs.background()
                    }

                    addActionListener {
                        focusProject(openProject)
                    }
                }
                add(button)
            }
            revalidate()
            repaint()
        }

        private fun focusProject(targetProject: Project) {
            val currentFrame = WindowManager.getInstance().getFrame(project)
            val targetFrame = WindowManager.getInstance().getFrame(targetProject)
            
            if (targetFrame != null) {
                if (currentFrame != null && currentFrame != targetFrame) {
                    targetFrame.bounds = currentFrame.bounds
                    targetFrame.extendedState = currentFrame.extendedState
                }
                targetFrame.toFront()
                targetFrame.requestFocus()
                ProjectTabsFactory.refreshAll()
            }
        }
    }
}
