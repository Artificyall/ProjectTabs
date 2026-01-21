package com.github.artificyal.projecttabs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureListener
import java.awt.dnd.DragSource
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Collections
import java.util.LinkedList
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.TransferHandler

class ProjectTabsFactory : IdeRootPaneNorthExtension {

    private val log = Logger.getInstance(ProjectTabsFactory::class.java)

    companion object {
        const val EXTENSION_ID = "com.github.artificyal.projecttabs.ProjectTabsFactory"
        private val panels = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ProjectTabsPanel, Boolean>()))
        var singleWindowMode = true

        private val projectOrder = Collections.synchronizedList(LinkedList<String>())

        fun getOrderedProjects(): List<Project> {
            val allProjects = ProjectManager.getInstance().openProjects.toList()
            val ordered = projectOrder.filter { path -> allProjects.any { it.basePath == path || it.name == path } }

            val newProjects = allProjects.filter { project ->
                val identifier = project.basePath ?: project.name
                !ordered.contains(identifier)
            }

            return (ordered.mapNotNull { path ->
                allProjects.find { (it.basePath ?: it.name) == path }
            } + newProjects).distinct()
        }

        fun reorderProjects(sourceIndex: Int, targetIndex: Int) {
            synchronized(projectOrder) {
                val allProjects = ProjectManager.getInstance().openProjects.toList()
                val currentOrder = projectOrder.toMutableList()

                val currentPaths = allProjects.map { it.basePath ?: it.name }
                val existingOrder = currentOrder.filter { currentPaths.contains(it) }
                val newProjects = currentPaths.filter { !currentOrder.contains(it) }
                val fullOrder = (existingOrder + newProjects).distinct().toMutableList()

                if (sourceIndex in fullOrder.indices && targetIndex in 0 until fullOrder.size && sourceIndex != targetIndex) {
                    val item = fullOrder.removeAt(sourceIndex)
                    val sizeBeforeRemove = fullOrder.size + 1

                    var adjustedTargetIndex = if (targetIndex > sourceIndex) targetIndex - 1 else targetIndex

                    if (targetIndex == sizeBeforeRemove - 1 && sourceIndex < targetIndex) {
                        adjustedTargetIndex = fullOrder.size
                    }

                    val finalIndex = adjustedTargetIndex.coerceIn(0, fullOrder.size)
                    fullOrder.add(finalIndex, item)

                    projectOrder.clear()
                    projectOrder.addAll(fullOrder)

                    refreshAll()
                }
            }
        }

        private fun initializeProjectOrder(projects: Array<Project>) {
            synchronized(projectOrder) {
                val currentPaths = projects.map { it.basePath ?: it.name }
                val existingOrder = projectOrder.toList()

                val preserved = existingOrder.filter { currentPaths.contains(it) }
                val newProjects = currentPaths.filter { !existingOrder.contains(it) }

                projectOrder.clear()
                projectOrder.addAll(preserved + newProjects)
            }
        }

        fun refreshAll() {
            ApplicationManager.getApplication().invokeLater {
                synchronized(panels) {
                    val iterator = panels.iterator()
                    while (iterator.hasNext()) {
                        try {
                            iterator.next().refreshTabs()
                        } catch (e: Exception) {
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
        log.info("ProjectTabsFactory.createComponent() for ${project.name}")
        val panel = ProjectTabsPanel(project)
        panels.add(panel)

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

        companion object {
            private const val PROJECT_PROPERTY = "projectTab.project"
            private const val INDEX_PROPERTY = "projectTab.index"
        }

        init {
            isOpaque = true
            background = com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
            layout = GridLayout(1, 0)
            border = JBUI.Borders.customLine(com.intellij.ui.JBColor(0x393b40, 0x393b40), 0, 0, 1, 0)
            transferHandler = PanelTransferHandler(this)
        }

        fun refreshTabs() {
            val openProjects = ProjectManager.getInstance().openProjects
            ProjectTabsFactory.initializeProjectOrder(openProjects)
            val orderedProjects = ProjectTabsFactory.getOrderedProjects().toTypedArray()

            if (componentCount == orderedProjects.size && componentCount > 0) {
                updateTabsUI(orderedProjects)
            } else {
                buildAllTabs(orderedProjects)
            }
        }

        private fun updateTabsUI(openProjects: Array<Project>) {
            for (i in openProjects.indices) {
                val openProject = openProjects[i]
                val isSelected = openProject == project

                val tabContainer = getComponent(i) as? JBPanel<*> ?: continue

                val layout = tabContainer.layout as? BorderLayout
                val nameLabel = layout?.getLayoutComponent(BorderLayout.CENTER) as? JLabel
                val closeButtonContainer = layout?.getLayoutComponent(BorderLayout.WEST) as? JBPanel<*>

                tabContainer.putClientProperty(PROJECT_PROPERTY, openProject)
                tabContainer.putClientProperty(INDEX_PROPERTY, i)

                closeButtonContainer?.isVisible = openProjects.size > 1

                tabContainer.background = if (isSelected) com.intellij.ui.JBColor(0x35373A, 0x35373A) else com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)

                nameLabel?.apply {
                    text = openProject.name
                    foreground = com.intellij.ui.JBColor(0x909090, 0x909090)
                    font = if (isSelected) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(12f)
                }
            }
        }

        private fun buildAllTabs(openProjects: Array<Project>) {
            removeAll()
            layout = GridLayout(1, openProjects.size)

            for ((index, openProject) in openProjects.withIndex()) {
                val isSelected = openProject == project

                val tabContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = true
                    background = if (isSelected) com.intellij.ui.JBColor(0x35373A, 0x35373A) else com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                    border = JBUI.Borders.customLine(com.intellij.ui.JBColor(0x393b40, 0x393b40), 0, 0, 0, 1)

                    val nameLabel = JLabel(openProject.name, SwingConstants.CENTER).apply {
                        foreground = com.intellij.ui.JBColor(0x909090, 0x909090)
                        font = if (isSelected) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(12f)
                    }
                    add(nameLabel, BorderLayout.CENTER)
                }

                tabContainer.putClientProperty(PROJECT_PROPERTY, openProject)
                tabContainer.putClientProperty(INDEX_PROPERTY, index)

                var closeButton: CloseButton? = null
                var closeButtonContainer: JBPanel<*>? = null
                if (openProjects.size > 1) {
                    closeButton = CloseButton(tabContainer)
                    closeButtonContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                        isOpaque = false
                        add(closeButton, BorderLayout.CENTER)
                        preferredSize = JBUI.size(20, 0)
                    }
                    tabContainer.add(closeButtonContainer, BorderLayout.WEST)
                }

                val dragSource = DragSource.getDefaultDragSource()
                dragSource.createDefaultDragGestureRecognizer(
                    tabContainer,
                    DnDConstants.ACTION_MOVE,
                    TabDragGestureListener(index, tabContainer)
                )

                var hideTimer: Timer? = null

                tabContainer.addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent?) {
                        hideTimer?.stop()
                        val tabProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                        if (tabProject != null && tabProject != project) {
                            tabContainer.background = com.intellij.ui.JBColor(0x2B2D30, 0x2B2D30)
                            tabContainer.repaint()
                        }
                        closeButton?.isTabHovered = true
                    }

                    override fun mouseExited(e: MouseEvent?) {
                        val tabProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                        if (tabProject != null && tabProject != project) {
                            tabContainer.background = com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                            tabContainer.repaint()
                        }
                        hideTimer?.stop()
                        val closeBtn = closeButton
                        val closeBtnContainer = closeButtonContainer
                        if (closeBtn != null && closeBtnContainer != null) {
                            hideTimer = Timer(50) {
                                val mouseLocation = java.awt.MouseInfo.getPointerInfo().location
                                val containerLocation = SwingUtilities.convertPoint(tabContainer, 0, 0, null)
                                val containerBounds = java.awt.Rectangle(containerLocation, tabContainer.size)

                                val closeButtonLocation = SwingUtilities.convertPoint(closeBtnContainer, 0, 0, null)
                                val closeButtonBounds = java.awt.Rectangle(closeButtonLocation, closeBtnContainer.size)

                                if (!containerBounds.contains(mouseLocation) && !closeButtonBounds.contains(mouseLocation)) {
                                    closeBtn.isTabHovered = false
                                }
                            }.apply {
                                isRepeats = false
                                start()
                            }
                        } else {
                            closeBtn?.isTabHovered = false
                        }
                    }

                    override fun mouseClicked(e: MouseEvent?) {
                        val tabProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                        if (tabProject != null && tabProject != project) {
                            ProjectTabsFactory.ensureSingleVisible(tabProject)
                        }
                    }
                })

                closeButtonContainer?.addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent?) {
                        hideTimer?.stop()
                        closeButton?.isTabHovered = true
                    }

                    override fun mouseExited(e: MouseEvent?) {
                        hideTimer?.stop()
                        val closeBtn = closeButton
                        if (closeBtn != null) {
                            hideTimer = Timer(50) {
                                val mouseLocation = java.awt.MouseInfo.getPointerInfo().location
                                val containerLocation = SwingUtilities.convertPoint(tabContainer, 0, 0, null)
                                val containerBounds = java.awt.Rectangle(containerLocation, tabContainer.size)

                                if (!containerBounds.contains(mouseLocation)) {
                                    closeBtn.isTabHovered = false
                                }
                            }.apply {
                                isRepeats = false
                                start()
                            }
                        }
                    }
                })

                add(tabContainer)
            }
            revalidate()
            repaint()
        }

        private class TabTransferable(private val projectIndex: Int) : Transferable {
            companion object {
                val DATA_FLAVOR = DataFlavor(Int::class.java, "Project Tab Index")
            }

            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DATA_FLAVOR)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DATA_FLAVOR

            override fun getTransferData(flavor: DataFlavor): Any {
                if (!isDataFlavorSupported(flavor)) {
                    throw UnsupportedFlavorException(flavor)
                }
                return projectIndex
            }
        }

        private class TabDragGestureListener(
            private val index: Int,
            private val sourceComponent: JComponent
        ) : DragGestureListener {
            override fun dragGestureRecognized(dge: DragGestureEvent) {
                val transferable = TabTransferable(index)
                dge.startDrag(null, transferable)
            }
        }

        private class PanelTransferHandler(
            private val panel: ProjectTabsPanel
        ) : TransferHandler() {
            override fun canImport(support: TransferHandler.TransferSupport): Boolean {
                return support.isDataFlavorSupported(TabTransferable.DATA_FLAVOR) &&
                       support.isDrop &&
                       support.dropAction == DnDConstants.ACTION_MOVE
            }

            override fun importData(support: TransferHandler.TransferSupport): Boolean {
                if (!canImport(support)) return false

                try {
                    val draggedIndex = support.transferable.getTransferData(TabTransferable.DATA_FLAVOR) as Int
                    val dropLocation = support.dropLocation

                    val point = dropLocation.dropPoint
                    val targetIndex = calculateDropIndex(panel, point)

                    if (targetIndex != -1 && draggedIndex != targetIndex && targetIndex < panel.componentCount) {
                        ProjectTabsFactory.reorderProjects(draggedIndex, targetIndex)
                        return true
                    }
                    return false
                } catch (e: Exception) {
                    return false
                }
            }

            private fun calculateDropIndex(panel: ProjectTabsPanel, point: java.awt.Point): Int {
                val componentCount = panel.componentCount
                if (componentCount == 0) return -1

                val x = point.x

                for (i in 0 until componentCount) {
                    val component = panel.getComponent(i)
                    val bounds = component.bounds
                    val centerX = bounds.x + bounds.width / 2

                    if (x < centerX) {
                        return i
                    }
                }

                return componentCount - 1
            }
        }
    }

    private class CloseButton(private val tabContainer: JComponent) : JComponent() {
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

            val crossSize = 6.0
            val thickness = 1.5
            g2.color = com.intellij.ui.JBColor(0xB0B0B0, 0xB0B0B0)
            g2.stroke = java.awt.BasicStroke(thickness.toFloat(), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)

            val offset = crossSize / 2

            g2.drawLine(
                (centerX - offset).toInt(),
                (centerY - offset).toInt(),
                (centerX + offset).toInt(),
                (centerY + offset).toInt()
            )

            g2.drawLine(
                (centerX + offset).toInt(),
                (centerY - offset).toInt(),
                (centerX - offset).toInt(),
                (centerY + offset).toInt()
            )
        }

        private fun closeProject() {
            ApplicationManager.getApplication().invokeLater {
                val targetProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                if (targetProject != null) {
                    ProjectManager.getInstance().closeProject(targetProject)
                }
            }
        }

        companion object {
            private const val PROJECT_PROPERTY = "projectTab.project"
        }
    }
}
