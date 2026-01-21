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
import javax.swing.TransferHandler

class ProjectTabsFactory() : IdeRootPaneNorthExtension {

    private val LOG = Logger.getInstance(ProjectTabsFactory::class.java)

    companion object {
        const val EXTENSION_ID = "com.github.artificyal.projecttabs.ProjectTabsFactory"
        private val PANELS = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ProjectTabsPanel, Boolean>()))
        var singleWindowMode = true
        
        // Gestion de l'ordre personnalisé des projets
        private val projectOrder = Collections.synchronizedList(LinkedList<String>())
        
        fun getOrderedProjects(): List<Project> {
            val allProjects = ProjectManager.getInstance().openProjects.toList()
            val ordered = projectOrder.filter { path -> allProjects.any { it.basePath == path || it.name == path } }
            
            // Ajouter les nouveaux projets à la fin
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
                
                // S'assurer que projectOrder contient tous les projets actuels
                val currentPaths = allProjects.map { it.basePath ?: it.name }
                val existingOrder = currentOrder.filter { currentPaths.contains(it) }
                val newProjects = currentPaths.filter { !currentOrder.contains(it) }
                val fullOrder = (existingOrder + newProjects).distinct().toMutableList()
                
                if (sourceIndex in fullOrder.indices && targetIndex in 0 until fullOrder.size && sourceIndex != targetIndex) {
                    val item = fullOrder.removeAt(sourceIndex)
                    val sizeBeforeRemove = fullOrder.size + 1 // taille avant le remove
                    
                    // Ajuster l'index cible après le retrait de l'élément source
                    // Si targetIndex > sourceIndex, après le remove, targetIndex devient targetIndex - 1
                    // Si targetIndex était le dernier élément (sizeBeforeRemove - 1), on veut mettre à la fin
                    var adjustedTargetIndex = if (targetIndex > sourceIndex) targetIndex - 1 else targetIndex
                    
                    // Cas spécial : si on déplace vers le dernier index, on veut vraiment mettre à la fin
                    if (targetIndex == sizeBeforeRemove - 1 && sourceIndex < targetIndex) {
                        adjustedTargetIndex = fullOrder.size // mettre à la fin
                    }
                    
                    // S'assurer que l'index ajusté est valide (add() accepte 0 à size inclus)
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
                
                // Préserver l'ordre existant pour les projets déjà présents
                val preserved = existingOrder.filter { currentPaths.contains(it) }
                val newProjects = currentPaths.filter { !existingOrder.contains(it) }
                
                projectOrder.clear()
                projectOrder.addAll(preserved + newProjects)
            }
        }

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
                val nameLabel = tabContainer.getComponent(0) as? JLabel

                // Mettre à jour la référence au projet et à l'index dans l'onglet
                tabContainer.putClientProperty(PROJECT_PROPERTY, openProject)
                tabContainer.putClientProperty(INDEX_PROPERTY, i)
                
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

                // Stocker la référence au projet et à l'index dans l'onglet
                tabContainer.putClientProperty(PROJECT_PROPERTY, openProject)
                tabContainer.putClientProperty(INDEX_PROPERTY, index)
                
                // Créer un DragSource pour permettre le drag
                val dragSource = DragSource.getDefaultDragSource()
                dragSource.createDefaultDragGestureRecognizer(
                    tabContainer,
                    DnDConstants.ACTION_MOVE,
                    TabDragGestureListener(index, tabContainer)
                )

                tabContainer.addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent?) {
                        val tabProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                        if (tabProject != project) {
                            tabContainer.background = com.intellij.ui.JBColor(0x2B2D30, 0x2B2D30)
                            tabContainer.repaint()
                        }
                    }

                    override fun mouseExited(e: MouseEvent?) {
                        val tabProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                        if (tabProject != project) {
                            tabContainer.background = com.intellij.ui.JBColor(0x1E1F22, 0x1E1F22)
                            tabContainer.repaint()
                        }
                    }

                    override fun mouseClicked(e: MouseEvent?) {
                        val tabProject = tabContainer.getClientProperty(PROJECT_PROPERTY) as? Project
                        if (tabProject != null && tabProject != project) {
                            ProjectTabsFactory.ensureSingleVisible(tabProject)
                        }
                    }
                })

                add(tabContainer)
            }
            revalidate()
            repaint()
        }
        
        // Classes internes pour le drag and drop
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
                       support.dropAction == MOVE
            }
            
            override fun importData(support: TransferHandler.TransferSupport): Boolean {
                if (!canImport(support)) return false
                
                try {
                    val draggedIndex = support.transferable.getTransferData(TabTransferable.DATA_FLAVOR) as Int
                    val dropLocation = support.dropLocation
                    
                    // Calculer l'index de destination basé sur la position de la souris
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
                
                // Convertir le point en coordonnées du panel si nécessaire
                val x = point.x
                
                // Trouver le composant sous la position x
                for (i in 0 until componentCount) {
                    val component = panel.getComponent(i)
                    val bounds = component.bounds
                    val centerX = bounds.x + bounds.width / 2
                    
                    if (x < centerX) {
                        return i
                    }
                }
                
                // Si on est au-delà du dernier composant, retourner la fin
                return componentCount - 1
            }
        }
    }

}
