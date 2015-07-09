package main.java.elegit;

import main.java.elegit.treefx.Cell;
import main.java.elegit.treefx.TreeGraph;
import main.java.elegit.treefx.TreeGraphModel;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the conversion/creation of a list of commit helpers into a nice
 * tree structure. It also takes care of updating the view its given to
 * display the new tree whenever the graph is updated.
 */
public abstract class CommitTreeModel{

    // The view corresponding to this model
    CommitTreePanelView view;

    // The model from which this class pulls its commits
    SessionModel sessionModel;
    // The graph corresponding to this model
    TreeGraph treeGraph;

    // A list of all branches this tree knows about
    private List<BranchHelper> branches;
    // A map from branch names to the branches themselves
    private Map<String, BranchHelper> branchMap;

    /**
     * Constructs a new commit tree model that supplies the data for the given
     * view
     * @param model the model with which this class accesses the commits
     * @param view the view that will be updated with the new graph
     */
    public CommitTreeModel(SessionModel model, CommitTreePanelView view){
        this.sessionModel = model;
        this.view = view;
        this.view.setName("Generic commit tree");
        CommitTreeController.allCommitTreeModels.add(this);
        this.init();
    }

    /**
     * Gets all commits tracked by this model and updates branch information
     * @return a list of all commits tracked by this model
     */
    private List<CommitHelper> getAllCommits(){
        if(this.sessionModel != null){
            RepoHelper repo = this.sessionModel.getCurrentRepoHelper();
            if(repo != null){
                List<CommitHelper> commits = getAllCommits(repo);
                this.branches = getAllBranches(repo);
                branchMap = new HashMap<>();
                for(BranchHelper branch : branches) branchMap.put(branch.getBranchName(),branch);
                return commits;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Gets any commits tracked by this model that haven't yet been recorded, and
     * updates branch information
     * @return a list of all new commits since the last update
     * @throws GitAPIException
     * @throws IOException
     */
    private List<CommitHelper> getNewCommits() throws GitAPIException, IOException{
        if(this.sessionModel != null){
            RepoHelper repo = this.sessionModel.getCurrentRepoHelper();
            if(repo != null){
                List<CommitHelper> commits = getNewCommits(repo, branchMap);
                this.branches = getAllBranches(repo);
                branchMap = new HashMap<>();
                for(BranchHelper branch : branches) branchMap.put(branch.getBranchName(),branch);
                return commits;
            }
        }
        return new ArrayList<>();
    }

    /**
     * @param repoHelper the repository to get the branches from
     * @return a list of all branches tracked by this model
     */
    protected abstract List<BranchHelper> getAllBranches(RepoHelper repoHelper);

    /**
     * @param repoHelper the repository to get the commits from
     * @return a list of all commits tracked by this model
     */
    protected abstract List<CommitHelper> getAllCommits(RepoHelper repoHelper);

    /**
     * @param repoHelper the repository to get the commits from
     * @param oldBranches the branches already known about
     * @return a list of all commits tracked by this model that haven't been added to the tree
     * @throws GitAPIException
     * @throws IOException
     */
    protected abstract List<CommitHelper> getNewCommits(RepoHelper repoHelper, Map<String, BranchHelper> oldBranches) throws GitAPIException, IOException;

    /**
     * @param id the id to check
     * @return true if the given id corresponds to a commit in the tree, false otherwise
     */
    public boolean containsID(String id){
        return treeGraph != null && treeGraph.treeGraphModel.containsID(id);
    }

    /**
     * Initializes the treeGraph, unselects any previously selected commit,
     * and then adds all commits tracked by this model to the tree
     */
    public synchronized void init(){
        treeGraph = this.createNewTreeGraph();

        CommitTreeController.resetSelection();

        this.addAllCommitsToTree();
        this.initView();
    }

    /**
     * Checks for new commits to add to the tree, and notifies the
     * CommitTreeController that an update is needed if there are any
     * @throws GitAPIException
     * @throws IOException
     */
    public synchronized void update() throws GitAPIException, IOException{
        if(this.addNewCommitsToTree()){
            this.updateView();
        }
    }

    /**
     * Adds a pseudo-cell of type InvisibleCell to the treeGraph.
     * @param id the id of the cell to add
     */
    public void addInvisibleCommit(String id){
        CommitHelper invisCommit = sessionModel.getCurrentRepoHelper().getCommit(id);
        for(CommitHelper c : invisCommit.getParents()){
            if(!treeGraph.treeGraphModel.containsID(c.getId())){
                addInvisibleCommit(c.getId());
            }
        }
        this.addCommitToTree(invisCommit, invisCommit.getParents(), treeGraph.treeGraphModel, false);
    }

    /**
     * Gets all commits tracked by this model and adds them to the tree
     * @return true if the tree was updated, otherwise false
     */
    private boolean addAllCommitsToTree() {
        return this.addCommitsToTree(this.getAllCommits());
    }

    /**
     * Gets all commits tracked by this model that haven't been added to the tree,
     * and adds them
     * @return true if the tree was updated, otherwise false
     * @throws GitAPIException
     * @throws IOException
     */
    private boolean addNewCommitsToTree() throws GitAPIException, IOException{
        return this.addCommitsToTree(this.getNewCommits());
    }

    /**
     * Adds the given list of commits to the treeGraph
     * @param commits the commits to add
     * @return true if commits where added, else false
     */
    private boolean addCommitsToTree(List<CommitHelper> commits){
        if(commits.size() == 0) return false;

        for(CommitHelper curCommitHelper : commits){
            List<CommitHelper> parents = curCommitHelper.getParents();
            this.addCommitToTree(curCommitHelper, parents, treeGraph.treeGraphModel, true);
        }

        treeGraph.update();
        return true;
    }

    /**
     * Creates a new TreeGraph with a new model. Updates the list
     * of all models accordingly
     * @return the newly created graph
     */
    private TreeGraph createNewTreeGraph(){
        TreeGraphModel graphModel = new TreeGraphModel();
        treeGraph = new TreeGraph(graphModel);
        return treeGraph;
    }

    /**
     * Adds a single commit to the tree with the given parents. Ensures the given parents are
     * already added to the tree, and if they aren't, adds them
     * @param commitHelper the commit to be added
     * @param parents a list of this commit's parents
     * @param graphModel the treeGraphModel to add the commit to
     */
    private void addCommitToTree(CommitHelper commitHelper, List<CommitHelper> parents, TreeGraphModel graphModel, boolean visible){
        List<String> parentIds = new ArrayList<>(parents.size());

        for(CommitHelper parent : parents){
            if(!graphModel.containsID(getId(parent))){
                addCommitToTree(parent, parent.getParents(), graphModel, visible);
            }
            parentIds.add(getId(parent));
        }

        String commitID = getId(commitHelper);
        if(graphModel.containsID(commitID) && graphModel.isVisible(commitID)){
            return;
        }

        graphModel.addCell(commitID, commitHelper.getWhen().getTime(), getTreeCellLabel(commitHelper), parentIds, visible);
    }

    /**
     * Updates the corresponding view if possible
     */
    private void updateView() throws IOException{
        if(this.sessionModel != null && this.sessionModel.getCurrentRepoHelper() != null){
            CommitTreeController.update(sessionModel.getCurrentRepoHelper());
        }else{
            view.displayEmptyView();
        }
    }

    /**
     * Initializes the corresponding view if possible
     */
    private void initView(){
        if(this.sessionModel != null && this.sessionModel.getCurrentRepoHelper() != null){
            CommitTreeController.init(this);
        }else{
            view.displayEmptyView();
        }
    }

    /**
     * Returns a string that will be displayed to the user to identify this commit
     * @param commitHelper the commit to get a label for
     * @return the display label for the commit
     */
    private String getTreeCellLabel(CommitHelper commitHelper){
        String s = "";
        if(branches != null){
            for(BranchHelper branch : branches){
                if(branch.getHead() != null && getId(branch.getHead()).equals(getId(commitHelper))){
                    s = s + "\n" + branch.getBranchName();
                }
            }
        }
        if(s.length() > 0){
            return commitHelper.getFormattedWhen() + "\n\nHead of branches: "+s;
        }else{
            return commitHelper.getFormattedWhen();
        }
    }

    /**
     * Returns a string that will be displayed to the user to identify the commit with the given id
     * @param commitId the id of the commit to get a label for
     * @return the display label for the commit
     */
    private String getTreeCellLabel(String commitId){
        return getTreeCellLabel(sessionModel.getCurrentRepoHelper().getCommit(commitId));
    }

    /**
     * Returns a unique identifier that will never be shown
     * @param commitHelper the commit to get an ID for
     * @return a unique identifying string to be used as a key in the tree's map
     */
    public static String getId(CommitHelper commitHelper){
        return commitHelper.getName();
    }

    /**
     * Marks the commit with the given id as the head of a tracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsTrackedBranch(String commitId){
        treeGraph.treeGraphModel.setCellShape(commitId, Cell.TRACKED_BRANCH_HEAD_SHAPE);
        treeGraph.treeGraphModel.setCellLabel(commitId, getTreeCellLabel(commitId));
    }

    /**
     * Marks the commit with the given id as the head of a tracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsTrackedBranch(ObjectId commitId){
        setCommitAsTrackedBranch(commitId.getName());
    }

    /**
     * Marks the commit with the given id as the head of an untracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsUntrackedBranch(String commitId){
        treeGraph.treeGraphModel.setCellShape(commitId, Cell.UNTRACKED_BRANCH_HEAD_SHAPE);
        treeGraph.treeGraphModel.setCellLabel(commitId, getTreeCellLabel(commitId));
    }

    /**
     * Marks the commit with the given id as the head of an untracked branch in the tree
     * @param commitId the id of the commit to mark
     */
    public void setCommitAsUntrackedBranch(ObjectId commitId){
        setCommitAsUntrackedBranch(commitId.getName());
    }

    /**
     * Forgets information about tracked/untracked branch heads in the tree
     */
    public void resetBranchHeads(boolean updateLabels){
        List<String> resetIDs = treeGraph.treeGraphModel.resetCellShapes();
        if(updateLabels){
            for(String id : resetIDs){
                treeGraph.treeGraphModel.setCellLabel(id, getTreeCellLabel(id));
            }
        }
    }

    /**
     * @return the branches tracked by this model
     */
    public List<BranchHelper> getBranches(){
        return branches;
    }
}
