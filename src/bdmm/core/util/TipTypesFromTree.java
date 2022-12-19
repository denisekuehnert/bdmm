package bdmm.core.util;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.TraitSet;
import beast.base.evolution.tree.Tree;

@Description("Acquire trait set values from tree leaf metadata.")
public class TipTypesFromTree extends TraitSet {

    public Input<String> typeLabelInput = new Input<>("typeLabel",
            "Type label used in tree metadata.",
            Input.Validate.REQUIRED);

    public Input<Tree> treeInput = new Input<>("tree", "Tree from which to " +
            "extract metadata.", Input.Validate.REQUIRED);

    public TipTypesFromTree() {
        traitsInput.setRule(Input.Validate.OPTIONAL);
    }

    @Override
    public void initAndValidate() {

        StringBuilder valueBuilder = new StringBuilder();

        boolean isFirst = true;
        for (Node leaf : treeInput.get().getExternalNodes()) {
            if (isFirst)
                isFirst = false;
            else
                valueBuilder.append(",");
            valueBuilder.append(leaf.getID()).append("=")
                    .append(leaf.getMetaData(typeLabelInput.get()));
        }

        traitsInput.setValue(valueBuilder.toString(), this);

        super.initAndValidate();
    }
}
