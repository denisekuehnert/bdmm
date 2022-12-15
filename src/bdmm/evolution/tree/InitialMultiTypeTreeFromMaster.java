package bdmm.evolution.tree;



import master.BeastTreeFromMaster;
import multitypetree.evolution.tree.MultiTypeNode;
import multitypetree.evolution.tree.MultiTypeTree;
import multitypetree.evolution.tree.SCMigrationModel;
import multitypetree.evolution.tree.StructuredCoalescentMultiTypeTree;

import java.util.Arrays;
import java.util.List;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.TraitSet;
import beast.base.inference.StateNode;
import beast.base.inference.StateNodeInitialiser;
import beast.base.inference.parameter.RealParameter;
import beast.base.util.Randomizer;

/**
 * User: Denise
 * Date: 11.06.14
 * Time: 14:35
 */
@Description("Make a MultiTypeTree with tip dates and states obtained from MASTER simulation")
public class InitialMultiTypeTreeFromMaster extends MultiTypeTree implements StateNodeInitialiser {

    final public Input<Alignment> taxaInput = new Input<>("taxa", "set of taxa to initialise tree specified by alignment");

    public Input<BeastTreeFromMaster> masterTreeInput = new Input<BeastTreeFromMaster>(
            "masterTree",
            "The tree from which traits should be inherited", Input.Validate.REQUIRED);

    public Input<Double> muInput = new Input<Double>("mu",
            "Migration rate for tree proposal", Input.Validate.REQUIRED);

    public Input<Double> popSizeInput = new Input<Double>("popSize",
            "Population size for tree proposal", Input.Validate.REQUIRED);

    public Input<Integer> nTypes = new Input<>("nTypes", "The number of types", Input.Validate.REQUIRED);

    public Input<Boolean> random = new Input<>("random", "Built random tree with traits from BeastTreeFromMaster? If false, tree will copy BeastTreeFromMaster (default true)", true);

    StateNode tree;

    @Override
    public void initAndValidate() {

        initStateNodes();
        super.initAndValidate();
    }

    @Override
    public void initStateNodes() {

        typeLabel = typeLabelInput.get();
//        nTypes = nTypesInput.get();

        BeastTreeFromMaster masterTree = masterTreeInput.get();

        TraitSet typeTrait = new TraitSet();
        TraitSet dateTrait = new TraitSet();

        String types = "";
        String dates = "";

        for (Node beastNode : masterTree.getExternalNodes()){

            dates += beastNode.getID() + "=" + beastNode.getHeight() +",";
            types += beastNode.getID() + "=" + beastNode.getMetaData("location") +",";

        }

        dates = dates.substring(0,dates.length()-1);
        types = types.substring(0,types.length()-1);

        Alignment taxa = taxaInput.get();
        TaxonSet taxonset = new TaxonSet();

        Alignment actualTaxa = new Alignment();
        int taxonCount = masterTree.getLeafNodeCount();
        for (int i=0; i<taxonCount; i++)
            actualTaxa.setInputValue("sequence", taxa.sequenceInput.get().get(i));
        actualTaxa.initAndValidate();

        taxonset.initByName("alignment", actualTaxa);
        setInputValue("taxonset",taxonset);

        typeTrait.initByName("value", types, "taxa", taxonset, "traitname", "type");
        dateTrait.initByName("value", dates, "taxa", taxonset, "traitname", "date-backward");

        SCMigrationModel migModel = new SCMigrationModel();

        Double[] temp = new Double[nTypes.get()];
        Arrays.fill(temp, muInput.get());
        migModel.setInputValue("rateMatrix", new RealParameter(temp));
        Arrays.fill(temp, popSizeInput.get());
        migModel.setInputValue("popSizes", new RealParameter(temp));
        migModel.initAndValidate();

        if (random.get()) {
            tree = new StructuredCoalescentMultiTypeTree();

            tree.setInputValue("migrationModel", migModel);
        }
        else{
            Node oldRoot = masterTree.getRoot();
            MultiTypeNode newRoot = new MultiTypeNode();
            
            newRoot.setHeight(oldRoot.getHeight());
            //newRoot.nTypeChanges = 0;
            //newRoot.changeTimes.addAll(new ArrayList<Double>());
            //newRoot.changeTypes.addAll(new ArrayList<Integer>());
            //newRoot.nodeType = 0;
            newRoot.setNr(oldRoot.getNr());
            //newRoot.labelNr = oldRoot.labelNr;

            newRoot.addChild(copyFromFlatNode(oldRoot.getLeft()));
            newRoot.addChild(copyFromFlatNode(oldRoot.getRight()));

            tree = new MultiTypeTree(newRoot);
        }

        tree.setInputValue("trait",typeTrait);
        tree.setInputValue("trait",dateTrait);
        tree.initAndValidate();

        setInputValue("trait",dateTrait);
        setInputValue("trait",typeTrait);

        assignFromWithoutID(tree);
    }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodeList) {
        stateNodeList.add(this);
    }

    MultiTypeNode copyFromFlatNode(Node node){

        MultiTypeNode mNode  = new MultiTypeNode();

        mNode.setHeight(node.getHeight());
        //mNode.parent = node.parent;
        mNode.setParent(node.getParent(), false);

        //mNode.nTypeChanges = 0;
       // mNode.changeTimes.addAll(new ArrayList<Double>());
       // mNode.changeTypes.addAll(new ArrayList<Integer>());
        //mNode.nodeType = 0;

        //mNode.labelNr = node.labelNr;
        mNode.setNr(node.getNr());

        if (node.isLeaf()){

            int type = (int) node.getMetaData("location");

            if (type!=0) {

                mNode.setNodeType(type);
                mNode.addChange(0, (node.getHeight() + (node.getParent().getHeight() -node.getHeight()) * Randomizer.nextDouble()));
            }

        } else {

            mNode.addChild(copyFromFlatNode(node.getLeft()));
            mNode.addChild(copyFromFlatNode(node.getRight()));
        }

         return mNode;
    }

}


