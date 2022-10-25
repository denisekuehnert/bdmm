package bdmm.core.util;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.TraitSet;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;

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
