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
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.Timer

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
                
                // La structure est maintenant : BorderLayout avec WEST (closeButtonContainer) et CENTER (nameLabel)
                val layout = tabContainer.layout as? java.awt.BorderLayout
                val nameLabel = layout?.getLayoutComponent(java.awt.BorderLayout.CENTER) as? JLabel

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

                val closeButton = CloseButton(openProject)
                val closeButtonContainer = JBPanel<JBPanel<*>>(java.awt.BorderLayout()).apply {
                    isOpaque = false
                    add(closeButton, java.awt.BorderLayout.CENTER)
                    preferredSize = JBUI.size(20, 0)
                }

                val tabContainer = JBPanel<JBPanel<*>>(java.awt.BorderLayout()).apply {
                    isOpaque = true
                    background = if (isSelected) com.intellij.ui.JBColor(0x35373A, 0x35373A) else com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                    border = JBUI.Borders.customLine(com.intellij.ui.JBColor(0x393b40, 0x393b40), 0, 0, 0, 1)

                    val nameLabel = JLabel(openProject.name, javax.swing.SwingConstants.CENTER).apply {
                        foreground = com.intellij.ui.JBColor(0x909090, 0x909090)
                        font = if (isSelected) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(12f)
                    }
                    add(closeButtonContainer, java.awt.BorderLayout.WEST)
                    add(nameLabel, java.awt.BorderLayout.CENTER)
                }

                var hideTimer: Timer? = null
                
                tabContainer.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                        hideTimer?.stop()
                        if (!isSelected) {
                            tabContainer.background = com.intellij.ui.JBColor(0x2B2D30, 0x2B2D30)
                            tabContainer.repaint()
                        }
                        closeButton.isTabHovered = true
                    }

                    override fun mouseExited(e: java.awt.event.MouseEvent?) {
                        if (!isSelected) {
                            tabContainer.background = com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                            tabContainer.repaint()
                        }
                        // Utiliser un timer pour vérifier après un court délai si la souris est toujours dans le tabContainer ou la croix
                        hideTimer?.stop()
                        hideTimer = Timer(50) {
                            val mouseLocation = java.awt.MouseInfo.getPointerInfo().location
                            val containerLocation = SwingUtilities.convertPoint(tabContainer, 0, 0, null)
                            val containerBounds = java.awt.Rectangle(containerLocation, tabContainer.size)
                            
                            val closeButtonLocation = SwingUtilities.convertPoint(closeButtonContainer, 0, 0, null)
                            val closeButtonBounds = java.awt.Rectangle(closeButtonLocation, closeButtonContainer.size)
                            
                            if (!containerBounds.contains(mouseLocation) && !closeButtonBounds.contains(mouseLocation)) {
                                closeButton.isTabHovered = false
                            }
                        }.apply {
                            isRepeats = false
                            start()
                        }
                    }

                    override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        if (openProject != project) {
                            ProjectTabsFactory.ensureSingleVisible(openProject)
                        }
                    }
                })
                
                closeButtonContainer.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                        hideTimer?.stop()
                        closeButton.isTabHovered = true
                    }
                    
                    override fun mouseExited(e: java.awt.event.MouseEvent?) {
                        // Vérifier si la souris est toujours dans le tabContainer
                        hideTimer?.stop()
                        hideTimer = Timer(50) {
                            val mouseLocation = java.awt.MouseInfo.getPointerInfo().location
                            val containerLocation = SwingUtilities.convertPoint(tabContainer, 0, 0, null)
                            val containerBounds = java.awt.Rectangle(containerLocation, tabContainer.size)
                            
                            if (!containerBounds.contains(mouseLocation)) {
                                closeButton.isTabHovered = false
                            }
                        }.apply {
                            isRepeats = false
                            start()
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

    private class CloseButton(private val targetProject: Project) : JComponent() {
        private var isHovered = false
        var isTabHovered = false
            set(value) {
                field = value
                isVisible = value || isHovered
                repaint()
            }

        init {
            isOpaque = false
            preferredSize = JBUI.size(16, 16)
            minimumSize = preferredSize
            maximumSize = preferredSize
            isVisible = false

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    isHovered = true
                    isVisible = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent?) {
                    isHovered = false
                    isVisible = isTabHovered
                    repaint()
                }

                override fun mouseClicked(e: MouseEvent?) {
                    e?.consume()
                    closeProject()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            if (!isVisible) {
                return
            }

            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val width = width
            val height = height
            val size = minOf(width, height)
            val centerX = width / 2.0
            val centerY = height / 2.0

            // Dessiner le fond arrondi si hover sur la croix
            if (isHovered) {
                val bgColor = com.intellij.ui.JBColor(0x4A4C50, 0x4A4C50)
                g2.color = bgColor
                val arcSize = 4.0
                g2.fillRoundRect(
                    (centerX - size / 2 + 2).toInt(),
                    (centerY - size / 2 + 2).toInt(),
                    (size - 4).toInt(),
                    (size - 4).toInt(),
                    arcSize.toInt(),
                    arcSize.toInt()
                )
            }

            // Dessiner la croix
            val crossSize = 6.0
            val thickness = 1.5
            g2.color = com.intellij.ui.JBColor(0xB0B0B0, 0xB0B0B0)
            g2.stroke = java.awt.BasicStroke(thickness.toFloat(), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)

            val offset = crossSize / 2

            // Ligne diagonale de haut gauche à bas droite
            g2.drawLine(
                (centerX - offset).toInt(),
                (centerY - offset).toInt(),
                (centerX + offset).toInt(),
                (centerY + offset).toInt()
            )

            // Ligne diagonale de haut droite à bas gauche
            g2.drawLine(
                (centerX + offset).toInt(),
                (centerY - offset).toInt(),
                (centerX - offset).toInt(),
                (centerY + offset).toInt()
            )
        }

        private fun closeProject() {
            ApplicationManager.getApplication().invokeLater {
                // Fermer le projet correctement via l'API IntelliJ
                // Cela déclenchera l'événement projectClosed qui rafraîchira automatiquement les onglets
                ProjectManager.getInstance().closeProject(targetProject)
            }
        }
    }
}
