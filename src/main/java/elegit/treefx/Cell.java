package main.java.elegit.treefx;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import main.java.elegit.CommitTreeController;
import main.java.elegit.MatchedScrollPane;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that represents a node in a TreeGraph
 */
public class Cell extends Pane{

    // Base shapes for different types of cells
    public static final CellShape DEFAULT_SHAPE = CellShape.SQUARE;
    public static final CellShape UNTRACKED_BRANCH_HEAD_SHAPE = CellShape.CIRCLE;
    public static final CellShape TRACKED_BRANCH_HEAD_SHAPE = CellShape.TRIANGLE_RIGHT;

    // The size of the rectangle being drawn
    public static final int BOX_SIZE = 30;

    // Limits on animation so the app doesn't begin to stutter
    private static final int MAX_NUM_CELLS_TO_ANIMATE = 5;
    private static int numCellsBeingAnimated = 0;

    // The displayed view
    Node view;
    private CellShape shape;
    // The tooltip shown on hover
    Tooltip tooltip;

    // The unique ID of this cell
    private final String cellId;
    // The assigned time of this commit
    private final long time;
    // The label displayed for this cell
    private String label;

    private ContextMenu contextMenu;

    // The list of children of this cell
    List<Cell> children = new ArrayList<>();

    // The parent object that holds the parents of this cell
    ParentCell parents;

    // All edges that have this cell as an endpoint
    List<Edge> edges = new ArrayList<>();

    // The row and column location of this cell
    public IntegerProperty columnLocationProperty, rowLocationProperty;

    // Whether this cell has been moved to its appropriate location
    public BooleanProperty hasUpdatedPosition;

    /**
     * Constructs a node with the given ID and a single parent node
     * @param cellId the ID of this node
     * @param parent the parent of this node
     */
    public Cell(String cellId, long time, Cell parent){
        this(cellId, time, parent, null);
    }

    /**
     * Constructs a node with the given ID and the two given parent nodes
     * @param cellId the ID of this node
     * @param parent1 the first parent of this node
     * @param parent2 the second parent of this node
     */
    public Cell(String cellId, long time, Cell parent1, Cell parent2){
        this.cellId = cellId;
        this.time = time;
        this.parents = new ParentCell(this, parent1, parent2);

        setShape(DEFAULT_SHAPE);

        this.columnLocationProperty = new SimpleIntegerProperty(-1);
        this.rowLocationProperty = new SimpleIntegerProperty(-1);

        this.hasUpdatedPosition = new SimpleBooleanProperty(false);
        visibleProperty().bind(this.hasUpdatedPosition);

        columnLocationProperty.addListener((observable, oldValue, newValue) -> hasUpdatedPosition.set(oldValue.intValue()==newValue.intValue()));
        rowLocationProperty.addListener((observable, oldValue, newValue) -> hasUpdatedPosition.set(oldValue.intValue()==newValue.intValue()));

        tooltip = new Tooltip(cellId);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        Tooltip.install(this, tooltip);

        this.setOnMouseClicked(event -> {
            if(event.getButton() == MouseButton.PRIMARY){
                CommitTreeController.handleMouseClicked(this.cellId);
            }else if(event.getButton() == MouseButton.SECONDARY){
                if(contextMenu != null){
                    contextMenu.show(this, event.getScreenX(), event.getScreenY());
                }
            }
            event.consume();
        });
        this.setOnMouseEntered(event -> CommitTreeController.handleMouseover(this, true));
        this.setOnMouseExited(event -> CommitTreeController.handleMouseover(this, false));
    }

    /**
     * Moves this cell to the given x and y coordinates
     * @param x the x coordinate to move to
     * @param y the y coordinate to move to
     * @param animate whether to animate the transition from the old position
     * @param emphasize whether to have the Highlighter class emphasize this cell while it moves
     */
    public void moveTo(double x, double y, boolean animate, boolean emphasize){
        if(animate && numCellsBeingAnimated < MAX_NUM_CELLS_TO_ANIMATE){
            numCellsBeingAnimated++;
            MatchedScrollPane.scrollTo(-1);

            Shape placeHolder = (Shape) getBaseView();
            placeHolder.setTranslateX(x+TreeLayout.H_PAD);
            placeHolder.setTranslateY(y);
            placeHolder.setOpacity(0.0);
            ((Pane)(this.getParent())).getChildren().add(placeHolder);

            TranslateTransition t = new TranslateTransition(Duration.millis(3000), this);
            t.setToX(x);
            t.setToY(y);
            t.setCycleCount(1);
            t.setOnFinished(event -> {
                numCellsBeingAnimated--;
                ((Pane)(this.getParent())).getChildren().remove(placeHolder);
            });
            t.play();

            if(emphasize){
                Highlighter.emphasizeCell(this);
            }
        }else{
            setTranslateX(x);
            setTranslateY(y);
        }
        this.hasUpdatedPosition.set(true);
    }

    /**
     * @return the basic view for this cell
     */
    protected Node getBaseView(){
        Node node = DEFAULT_SHAPE.get();
        node.setStyle("-fx-fill: " + CellState.STANDARD.getCssStringKey());
        node.getStyleClass().setAll("cell");
        return node;
    }

    /**
     * Sets the look of this cell
     * @param newView the new view
     */
    public synchronized void setView(Node newView) {
        if(this.view == null){
            this.view = getBaseView();
        }

        newView.setStyle(this.view.getStyle());
        newView.getStyleClass().setAll(this.view.getStyleClass());

        this.view = newView;
        getChildren().clear();
        getChildren().add(newView);
    }

    /**
     * Set the shape of this cell
     * @param newShape the new shape
     */
    public synchronized void setShape(CellShape newShape){
        if(this.shape == newShape) return;
        setView(newShape.get());
        this.shape = newShape;
    }

    /**
     * Sets the tooltip to display the given text
     * @param label the text to display
     */
    public void setDisplayLabel(String label){
        tooltip.setText(label);
        this.label = label;
    }

    public void setContextMenu(ContextMenu contextMenu){
        this.contextMenu = contextMenu;
    }

    /**
     * Adds a child to this cell
     * @param cell the new child
     */
    public void addCellChild(Cell cell) {
        children.add(cell);
    }

    /**
     * @return the list of the children of this cell
     */
    public List<Cell> getCellChildren() {
        return children;
    }

    /**
     * @return the list of the parents of this cell
     */
    public List<Cell> getCellParents(){
        return parents.toList();
    }

    /**
     * Removes the given cell from the children of this cell
     * @param cell the cell to remove
     */
    public void removeCellChild(Cell cell) {
        children.remove(cell);
    }

    /**
     * Checks to see if the given cell has this cell as an ancestor,
     * up to the given number of generations.
     *
     * Entering zero or a negative number will search all descendants
     *
     * @param cell the commit to check
     * @param depth how many generations down to check
     * @return true if cell is a descendant of this cell, otherwise false
     */
    public boolean isChild(Cell cell, int depth){
        depth--;
        if(children.contains(cell)) return true;
        else if(depth != 0){
            for(Cell child : children){
                if(child.isChild(cell, depth)){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sets the state of this cell and adjusts the style accordingly
     * @param state the new state of the cell
     */
    public void setCellState(CellState state){
        view.setStyle("-fx-fill: "+state.getCssStringKey());
    }

    /**
     * @return the unique ID of this cell
     */
    public String getCellId() {
        return cellId;
    }

    /**
     * @return the time of this cell
     */
    public long getTime(){
        return time;
    }

    /**
     * A class that holds the parents of a cell
     */
    private class ParentCell{

        private Cell mom,dad;

        /**
         * Sets the given child to have the given parents
         * @param child the child cell
         * @param mom the first parent
         * @param dad the second parent
         */
        public ParentCell(Cell child, Cell mom, Cell dad){
            this.mom = mom;
            this.dad = dad;
            this.setChild(child);
        }

        /**
         * @return the number of parent commits associated with this object
         */
        public int count(){
            int count = 0;
            if(mom != null) count++;
            if(dad != null) count++;
            return count;
        }

        /**
         * @return the stored parent commits in list form
         */
        public ArrayList<Cell> toList(){
            ArrayList<Cell> list = new ArrayList<>(2);
            if(mom != null) list.add(mom);
            if(dad != null) list.add(dad);
            return list;
        }

        /**
         * Sets the given sell to be the child of each non-null parent
         * @param cell the child to add
         */
        private void setChild(Cell cell){
            if(this.mom != null){
                this.mom.addCellChild(cell);
            }
            if(this.dad != null){
                this.dad.addCellChild(cell);
            }
        }
    }
}