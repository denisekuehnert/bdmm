package beast.core.util;

import beast.core.Description;
import beast.core.Input;
import beast.evolution.tree.Node;
import beast.evolution.tree.TraitSet;
import beast.evolution.tree.Tree;
import beast.util.TreeParser;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Traitset for tip dates obtained from input tree")
public class TipDatesFromTree extends TipTypesFromTree {

    public TipDatesFromTree() {
        super();
        traitNameInput.setRule(Input.Validate.OPTIONAL);
    }


    @Override
    public void initAndValidate() {

        traitNameInput.setValue("date-backward", this);
        typeLabelInput.setValue("date", this);
        super.initAndValidate();
    }
}
