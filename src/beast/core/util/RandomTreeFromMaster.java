package beast.core.util;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.StateNode;
import beast.base.inference.StateNodeInitialiser;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.TraitSet;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.coalescent.RandomTree;
import beast.evolution.tree.*;

import java.util.List;

/**
 * User: Denise
 * Date: 06.06.14
 * Time: 16:34
 */
@Description("Make a random tree with tip dates and states obtained from MASTER simulation")
public class RandomTreeFromMaster extends Tree implements StateNodeInitialiser {

    public Input<BeastTreeFromMaster> masterTreeInput = new Input<BeastTreeFromMaster>(
            "masterTree",
            "The tree from which traits should be inherited", Input.Validate.REQUIRED);

    public Input<StateNode> treeInput = new Input<StateNode>(
            "tree",
            "The initial tree which is to inherited traits from masterTree", Input.Validate.REQUIRED);

    public Input<Alignment> taxaInput = new Input<Alignment>("taxa", "set of taxa to initialise tree specified by alignment");


    StateNode tree;

    @Override
    public void initAndValidate() {


        super.initAndValidate();

        initStateNodes();
    }

    public void initStateNodes() {

        BeastTreeFromMaster masterTree = masterTreeInput.get();


        tree  = new RandomTree();

        TraitSet typeTrait = new TraitSet();
        TraitSet dateTrait = new TraitSet();

        Alignment taxa = taxaInput.get();
        TaxonSet taxonset = new TaxonSet();
        taxonset.initByName("alignment", taxa);

        String types = "";
        String dates = "";

        for (Node beastNode : masterTree.getExternalNodes()){

            dates += beastNode.getID() + "=" + beastNode.getHeight() +",";
            types += beastNode.getID() + "=" + (beastNode.getMetaData("location")) +",";

        }

        dates = dates.substring(0,dates.length()-1);
        types = types.substring(0,types.length()-1);

        typeTrait.initByName("value", types, "taxa", taxonset, "traitname", "type");
        dateTrait.initByName("value", dates, "taxa", taxonset, "traitname", "date-backward");

        tree.initByName("trait",dateTrait,"trait",typeTrait, "taxa", taxa, "populationModel", ((RandomTree)treeInput.get()).populationFunctionInput.get());

        treeInput.get().setInputValue("trait",dateTrait);
        treeInput.get().setInputValue("trait",typeTrait);
        treeInput.get().assignFromWithoutID(tree);
    }

    public void getInitialisedStateNodes(List<StateNode> stateNodes){

        stateNodes.add(treeInput.get());
    }

}



