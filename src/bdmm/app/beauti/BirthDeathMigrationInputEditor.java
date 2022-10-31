package bdmm.app.beauti;

import beastfx.app.inputeditor.InputEditor;
import beastfx.app.util.FXUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingNode;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.TextAlignment;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;



import java.util.ArrayList;

import bdmm.evolution.speciation.BirthDeathMigrationModel;
import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.inference.parameter.RealParameter;
import beastfx.app.inputeditor.BeautiDoc;

/**
 * Created by Denise on 04.07.16.
 */
public class BirthDeathMigrationInputEditor extends InputEditor.Base {

	
	CheckBox R0EstCheckBox, deltaEstCheckBox, samplingEstCheckBox, rateMatrixEstCheckBox;
    HBox r0Box, deltaBox, samplingBox, samplingChangeBox;
    List<TextField> r0ModelVals, deltaModelVals, samplingModelVals, samplingChangeModelVals;
    
    
    VBox rateMatrixTextFieldBox;
    List<HBox> rateMatrixBoxes;
    List<List<TextField>> rateMatrixModelVals;
    
    Spinner<Integer> dimSpinner;
    
    BirthDeathMigrationModel bdmm;
    
    
    
    boolean initialising = false;
    boolean dimChangeInProgress = false;
    boolean loadingInProgress = false;

    public BirthDeathMigrationInputEditor(BeautiDoc doc) {
        super(doc);
    }

    @Override
    public Class<?> type() {
        return BirthDeathMigrationModel.class;
    }
    
    



    @Override
    public void init(Input<?> input, BEASTInterface beastObject, int itemNr, ExpandOption bExpandOption, boolean bAddButtons) {

    	initialising = true;
    	
    	m_input = input;
    	m_beastObject = beastObject;
		this.itemNr = itemNr;
		pane = FXUtils.newVBox();
		this.bdmm = (BirthDeathMigrationModel) ((ArrayList) input.get()).get(0);
		

		VBox outerBox = FXUtils.newVBox();
		
        // 3 columns
		GridPane gridPane = new GridPane();
		gridPane.setHgap(10);
		gridPane.setVgap(10);

		
		// Number of demes
		Label nrOfDemesLabel = new Label("Number of demes:");
        dimSpinner = new Spinner<>(2, 100, 2);
        dimSpinner.setEditable(true);
        dimSpinner.setPrefWidth(100);
        gridPane.add(nrOfDemesLabel, 0, 0);
        gridPane.add(dimSpinner, 1, 0);
        
        
        int ndim = dimSpinner.getValue();
        
       
        // Init reproduction number
        r0Box = new HBox();
        r0ModelVals = new ArrayList<>();
        R0EstCheckBox = new CheckBox("estimate");
        this.initParameter(gridPane, 1, bdmm.R0.get(), "Reproduction number per type:", r0Box, r0ModelVals, R0EstCheckBox, bdmm.R0.getTipText(), ndim);

        
        // Init delta
        deltaBox = new HBox();
        deltaModelVals = new ArrayList<>();
        deltaEstCheckBox = new CheckBox("estimate");
        this.initParameter(gridPane, 2, bdmm.becomeUninfectiousRate.get(),"BecomeUninfectionRate per type:", deltaBox, deltaModelVals, deltaEstCheckBox, bdmm.becomeUninfectiousRate.getTipText(), ndim);
        
        
        // Init sampling proportion
        samplingBox = new HBox();
        samplingModelVals = new ArrayList<>();
        samplingEstCheckBox = new CheckBox("estimate");
        this.initParameter(gridPane, 3, bdmm.samplingProportion.get(), "SamplingProportion per type:", samplingBox, samplingModelVals, samplingEstCheckBox, bdmm.samplingProportion.getTipText(), ndim*2);

        
        
        // Init sampling change times but hide the first box
        samplingChangeBox = new HBox();
        samplingChangeModelVals = new ArrayList<>();
        this.initParameter(gridPane, 4, bdmm.samplingRateChangeTimesInput.get(), "Sampling change time:", samplingChangeBox, samplingChangeModelVals, null, bdmm.samplingRateChangeTimesInput.getTipText(), 2);
        samplingChangeModelVals.get(0).setVisible(false);
        samplingChangeModelVals.get(0).setManaged(false);
        
        
        // Init migration matrix
        rateMatrixTextFieldBox = new VBox();
        rateMatrixBoxes = new ArrayList<>();
        rateMatrixModelVals = new ArrayList<>();
        rateMatrixEstCheckBox = new CheckBox("estimate");
    	this.initMatrix(gridPane, 5, bdmm.migrationMatrix.get(), rateMatrixTextFieldBox, "Migration rates:", rateMatrixBoxes, rateMatrixModelVals, rateMatrixEstCheckBox, bdmm.migrationMatrix.getTipText(), ndim);
        	  
        
        
        outerBox.getChildren().add(gridPane);
        pane.getChildren().add(outerBox);
        getChildren().add(pane);
        
        
        initialising = false;
        
        loadFromBDMM();
        
        
        /**
         * Action listeners
         */
        
        // Dimension spinner
        dimSpinner.valueProperty().addListener((observable, oldDim, newDim) -> {
        	
        	 if (loadingInProgress || dimChangeInProgress) return;
             //int oldDim = bdmm.stateNumber.get();

             dimChangeInProgress = true;

             System.out.println("Dimension change starting from " + oldDim + " to " + newDim);


             // Update R0
             setVectorDimension(bdmm.R0.get(), r0Box, r0ModelVals, oldDim, newDim, bdmm.R0.getTipText());
             
             // Update delta
             setVectorDimension(bdmm.becomeUninfectiousRate.get(), deltaBox, deltaModelVals, oldDim, newDim, bdmm.becomeUninfectiousRate.getTipText());

             // Update sampling proportion
             setVectorDimension(bdmm.samplingProportion.get(), samplingBox, samplingModelVals, oldDim*2, newDim*2, bdmm.samplingProportion.getTipText());

             // Update sampling change times 
             setVectorDimension(bdmm.samplingRateChangeTimesInput.get(), samplingChangeBox, samplingChangeModelVals, 2, 2, bdmm.samplingRateChangeTimesInput.getTipText());

             // Update migration matrix 
             setMatrixDimension(bdmm.migrationMatrix.get(), rateMatrixTextFieldBox, rateMatrixBoxes, rateMatrixModelVals, newDim, bdmm.migrationMatrix.getTipText());
             
             
             System.out.println(" r0ModelVals " + r0ModelVals.size());
             
             
            
             
             // Ensure frequencies sum to 1
             StringBuilder sbfreqs = new StringBuilder();
             double fr = Math.round(100./newDim)/100.;
             for (int i=0; i<newDim; i++) {
                 if (i>0)
                     sbfreqs.append(" ");

                 if (i==0)   // make sure frequencies add up to 1
                     sbfreqs.append(Double.toString(Math.round(100*(1-(newDim-1)*fr))/100.));
                 else
                     sbfreqs.append(Double.toString(fr));

             }
            
             bdmm.frequencies.get().valuesInput.setValue(sbfreqs.toString(), bdmm.frequencies.get());
             bdmm.frequencies.get().setDimension(newDim);
             bdmm.frequencies.get().initAndValidate();
             bdmm.stateNumber.setValue(newDim, bdmm);
             //bdmm.setInputValue("stateNumber", newDim);
             
             saveParameters();
             
             System.out.println("Dimension change finishing.");

             
             dimChangeInProgress = false;
             

        	
        });


    
    }
    
    
    
    /**
     * Add a parameter to the gui and return its estimate checkbox hbox
     * @param name
     * @param hbox
     * @param vector
     * @param checkBox
     * @param tooltip
     * @return
     */
    private void initParameter(GridPane gridPane, int rowNum, RealParameter param, String name, HBox hbox, List<TextField> vector, CheckBox checkBox, String tooltip, int ndim) {
    	
        
    	// Label
        Label label = new Label(name);
        label.setTooltip(new Tooltip(tooltip));
        
        
        // Add textfields for editing values
        this.setVectorDimension(param, hbox, vector, vector.size(), ndim, tooltip);
        
        // Add estimate checkbox
        if (checkBox != null) {
	        checkBox.setSelected(bdmm.R0.get().isEstimatedInput.get());
	        checkBox.setTooltip(new Tooltip("Estimate value of this parameter in the MCMC chain"));
	        
	        
	        // Checkbox event listener
	        checkBox.setOnAction(e -> {
	        	saveToBDMM();
	        });
        }
        
        // Add to gridpane
        gridPane.add(label, 0, rowNum); // Column 0
        gridPane.add(hbox, 1, rowNum); // Column 1
        if (checkBox != null) gridPane.add(checkBox, 2, rowNum); // Column 2
        
    	
    }
    
    /**
     * Same as above but for a matrix
     * @param gridPane
     * @param rowNum
     * @param param
     * @param name
     * @param hboxes
     * @param vector
     * @param checkBox
     * @param tooltip
     * @param ndim
     */
    private void initMatrix(GridPane gridPane, int rowNum, RealParameter param, VBox vbox, String name, List<HBox> hboxes, List<List<TextField>> matrix, CheckBox checkBox, String tooltip, int ndim) {
    	
    	// Label
        Label label = new Label(name);
        label.setTooltip(new Tooltip(tooltip));
        
        
        // Add textfields for editing values
        hboxes.clear();
        int entryNum = 0;
        for (int row = 0; row < ndim; row++) {
        	HBox hb = new HBox();
        	
        	List<TextField> vec = new ArrayList<>();
        	//this.setVectorDimension(param, hb, vec, vec.size(), ndim, tooltip);
        	for (int col = 0; col < ndim; col++) {
    			
    			
    			TextField tf;
    			if (row == col) {
    				
    				// Disable the diagonal elements
    				tf = createTextField(param, -1, hb, tooltip, 1);
    				tf.setDisable(true);
    				tf.setText("");
    			}else {
    				tf = createTextField(param, entryNum, hb, tooltip, 1);
    				entryNum ++;
    			}
    			vec.add(tf);
    			
       	 	}
        	
        	vbox.getChildren().add(hb);
        	hboxes.add(hb);
        	matrix.add(vec);
        }
        
        
        // Add estimate checkbox
        if (checkBox != null) {
	        checkBox.setSelected(bdmm.R0.get().isEstimatedInput.get());
	        checkBox.setTooltip(new Tooltip("Estimate value of this parameter in the MCMC chain"));
	        
	        
	        // Checkbox event listener
	        checkBox.setOnAction(e -> {
	        	saveToBDMM();
	        });
        }
        
        // Add to gridpane
        gridPane.add(label, 0, rowNum); // Column 0
        gridPane.add(vbox, 1, rowNum); // Column 1
        if (checkBox != null) gridPane.add(checkBox, 2, rowNum); // Column 2
        
    	
    }
    
    
    /**
     * Saves a parameter from the gui to bdmm
     * @param param
     * @param vector
     */
    private void saveParameter(RealParameter param, List<TextField> vector, Boolean selected, int ndim) {
    	
    	
    	
    	if (selected != null) {
    		param.isEstimatedInput.setValue(selected, param);
    	}
    	
    	/*
    	for (int i = 0; i < vector.size(); i++) {
    		String val = vector.get(i).getText();
    		double x;
    		try {
    			x = Double.parseDouble(val);
    			param.setValue(i, x);
    		}catch(Exception e) {
    			
    		}
    	}
    	*/
    	 StringBuilder sb = new StringBuilder();
    	 for (int i = 0; i < ndim; i++) {
    		 String val = vector.get(i).getText();
             if (i>0) sb.append(" ");
             sb.append(val);
         }
    	 param.valuesInput.setValue(sb.toString(), param);
    	 param.setDimension(ndim);
         //bdmm.setInputValue(input.getName(), sb.toString());
         
    	
    }
    
    
    /**
     * Saves a parameter from the gui to bdmm
     * @param param
     * @param vector
     */
    private void saveMatrix(RealParameter param, List<List<TextField>> matrix, Boolean selected, int ndim) {
    	
    	
    	
    	if (selected != null) {
    		param.isEstimatedInput.setValue(selected, param);
    	}
    	
    	
    	 StringBuilder sb = new StringBuilder();
    	 int entryNum = 0;
    	 for (int row = 0; row < ndim; row++) {
    		System.out.println("row " + row);
         	List<TextField> vec = matrix.get(row);
         	
         	
         	
         	for (int col = 0; col < ndim; col++) {
         		System.out.println("row " + row + " col " + col);
        		if (row == col) continue;
         	
        		String val = vec.get(col).getText();
        		if (entryNum>0) sb.append(" ");
        		sb.append(val);
        		entryNum++;
        		
         	}
         }
    	 param.valuesInput.setValue(sb.toString(), param);
    	 param.setDimension(ndim*(ndim-1));
    	
   
    }
    
    
    /**
     * Loads a parameter from the bdmm to the gui
     * @param param
     * @param vector
     */
    private void loadParameter(RealParameter param, List<TextField> vector, HBox hbox, CheckBox checkBox, String tipText) {
    	
    	
    	
    	// Estimate?
    	if (checkBox != null) {
    		checkBox.setSelected(param.isEstimatedInput.get());
    	}
    	
    	
    	//System.out.println("loading " + param.getID() + " dimension to " + param.getDimension());
    	
    	// Resize the vectors
    	this.setVectorDimension(param, hbox, vector, param.getDimension(), param.getDimension(), tipText);
    	
    	// Load their values into javafx
    	for (int i = 0; i < param.getDimension(); i++) {
    		TextField tf = vector.get(i);
    		double val = param.getArrayValue(i);
    		tf.setText("" + val);
    	}
    	
    }
    
    
    /**
     * Same as above but for a matrix
     * @param param
     * @param rateMatrixModelVals2
     * @param rateMatrixBoxes2
     * @param rateMatrixEstCheckBox2
     * @param tipText
     */
    private void loadMatrix(RealParameter param, VBox vbox, List<List<TextField>> matrix, List<HBox> hboxes, CheckBox checkBox, String tooltip, int ndim) {
		
    	

    	
    	// Estimate?
    	if (checkBox != null) {
    		checkBox.setSelected(param.isEstimatedInput.get());
    	}
    	
    	// Add textfields for editing values
    	this.setMatrixDimension(param, vbox, hboxes, matrix, ndim, tooltip);
    	
    	
    	// Load their values into javafx
    	int entryNum = 0;
    	for (int row = 0; row < ndim; row++) {
    		List<TextField> vec = matrix.get(row);
	    	for (int col = 0; col < ndim; col++) {
	    		if (row == col) continue;
	    		TextField tf = vec.get(col);
	    		double val = param.getArrayValue(entryNum);
	    		tf.setText("" + val);
	    		entryNum++;
	    	}
    	}
    	
    	
        
     
		
	}
    
    
    
    /**
     * Resize a vector of textfields
     * @param hbox
     * @param vector
     * @param newDim
     * @param tipText
     */
    private void setVectorDimension(RealParameter param, HBox hbox, List<TextField> vector, int oldDim, int newDim, String tipText) {
    	oldDim = Math.min(oldDim, vector.size());
    	
    	if (oldDim == newDim) return;
    	
    	if (oldDim < newDim) {
    		
    		
    		
    		
    		for (int i = oldDim; i < newDim; i++) {
    			
    			double defaultVal = oldDim == 0 ? 1 : param.getArrayValue(0);
    			
    			// Special case: ensure that every new second value of sampling proportion is non-zero
        		if (i % 2 == 1 && param == bdmm.samplingProportion.get()) {
        			defaultVal = 0.01;
        		}
        		
    			
    			TextField tf = createTextField(param, i, hbox, tipText, defaultVal);
    			vector.add(tf);
       	 	}
        }else {
        	for (int i = oldDim-1; i >= newDim; i--) {
        		TextField tf = vector.get(i);
        		hbox.getChildren().remove(tf);
        		vector.remove(i);
			}
        }
    	
    	
    	//System.out.println(param.getID() + " dimension to "+ vector.size());
    	
    }
    
    
    /**
     * Resize a matrix of textfields
     * @param param
     * @param vbox
     * @param hboxes
     * @param matrix
     * @param oldDim
     * @param newDim
     * @param tooltip
     */
    private void setMatrixDimension(RealParameter param, VBox vbox, List<HBox> hboxes, List<List<TextField>> matrix, int ndim, String tooltip) {
    	
    	
    	double defaultVal = param.getArrayValue(0);

        // Add textfields for editing values
    	vbox.getChildren().clear();
        hboxes.clear();
        matrix.clear();
        int entryNum = 0;
        for (int row = 0; row < ndim; row++) {
        	HBox hb = new HBox();
        	
        	List<TextField> vec = new ArrayList<>();
        	//this.setVectorDimension(param, hb, vec, vec.size(), ndim, tooltip);
        	for (int col = 0; col < ndim; col++) {
    			
    			
    			TextField tf;
    			if (row == col) {
    				
    				// Disable the diagonal elements
    				tf = createTextField(param, -1, hb, tooltip, 0);
    				tf.setDisable(true);
    				tf.setText("");
    			}else {
    				tf = createTextField(param, entryNum, hb, tooltip, defaultVal);
    				entryNum ++;
    			}
    			vec.add(tf);
    			
       	 	}
        	
        	vbox.getChildren().add(hb);
        	hboxes.add(hb);
        	matrix.add(vec);
        }
        
        

    }
    
    
    /**
     * Create a textfield
     * @param hbox
     * @param val
     * @param toopTip
     * @return
     */
    private TextField createTextField(RealParameter param, int index, HBox hbox, String toopTip, double val) {
    	
    	
    	TextField tf = new TextField();
    	tf.setText("" + val);
    	tf.setTooltip(new Tooltip(toopTip));
    	tf.setPrefWidth(70);
    	
    	
    	tf.textProperty().addListener((observable, oldValue, newValue) -> {
    		if (!oldValue.equals(newValue) && !this.loadingInProgress && !this.dimChangeInProgress && !this.initialising) {
    			//System.out.println("change in " + param.getID() + ": " + oldValue + " to " + newValue);

    			try {
        			double x = Double.parseDouble(newValue);
        			param.setValue(index, x);
        		}catch(Exception e) {
        			
        		}
    			saveParameters();
    		}
    	});

    	
    	hbox.getChildren().add(tf);
    	return tf;
    }

    
    /**
     * Load from bdmm into this class
     */
    public void loadFromBDMM() {
    	
    	
    	if (dimChangeInProgress || loadingInProgress) return;
    	
    	loadingInProgress = true;
    	
    	
    	int ndim = bdmm.stateNumber.get();
    	
    	dimSpinner.getValueFactory().setValue(ndim);
    
    	this.loadParameter(bdmm.R0.get(), r0ModelVals, r0Box, R0EstCheckBox, bdmm.R0.getTipText());
    	this.loadParameter(bdmm.becomeUninfectiousRate.get(), deltaModelVals, deltaBox, deltaEstCheckBox, bdmm.becomeUninfectiousRate.getTipText());
    	this.loadParameter(bdmm.samplingProportion.get(), samplingModelVals, samplingBox, samplingEstCheckBox, bdmm.samplingProportion.getTipText());
    	this.loadParameter(bdmm.samplingRateChangeTimesInput.get(), samplingChangeModelVals, samplingChangeBox, null, bdmm.samplingRateChangeTimesInput.getTipText());
    	this.loadMatrix(bdmm.migrationMatrix.get(), rateMatrixTextFieldBox, rateMatrixModelVals, rateMatrixBoxes, rateMatrixEstCheckBox, bdmm.migrationMatrix.getTipText(), ndim);
    	
    	
    	
    	loadingInProgress = false;
    	
    }

    
    public void saveParameters() {
    	
    	Integer ndim = dimSpinner.getValue();
    	
    	
    	// Parse parameters
    	this.saveParameter(bdmm.R0.get(), r0ModelVals, R0EstCheckBox.isSelected(), ndim);
    	this.saveParameter(bdmm.becomeUninfectiousRate.get(), deltaModelVals, deltaEstCheckBox.isSelected(), ndim);
    	this.saveParameter(bdmm.samplingProportion.get(), samplingModelVals, samplingEstCheckBox.isSelected(), ndim*2);
    	this.saveParameter(bdmm.samplingRateChangeTimesInput.get(), samplingChangeModelVals, null, 2);
    	this.saveMatrix(bdmm.migrationMatrix.get(), rateMatrixModelVals, rateMatrixEstCheckBox.isSelected(), ndim);
    	
    	try {
            bdmm.R0.get().initAndValidate();
            bdmm.samplingProportion.get().initAndValidate();
            bdmm.samplingRateChangeTimesInput.get().initAndValidate();
            bdmm.becomeUninfectiousRate.get().initAndValidate();
            bdmm.migrationMatrix.get().initAndValidate();
            bdmm.initAndValidate();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Error updating tree prior.");
        }
    	
    	
    	/*
    	try {
            bdmm.R0.get().initAndValidate();
            bdmm.samplingProportion.get().initAndValidate();
            bdmm.samplingRateChangeTimesInput.get().initAndValidate();
            bdmm.becomeUninfectiousRate.get().initAndValidate();
            bdmm.migrationMatrix.get().initAndValidate();
            bdmm.initAndValidate();
        } catch (Exception ex) {
            System.err.println(ex.getCause());
            System.err.println("Error updating tree prior.");
        }
    	*/


    }
  

	public void saveToBDMM() {
    	
    	
    	if (dimChangeInProgress || loadingInProgress) return;
    	loadingInProgress = true;
    	
    	// Dimension
    	Integer ndim = dimSpinner.getValue();
    	bdmm.setInputValue("stateNumber", ndim);
    	
    	saveParameters();
    	
    	

        
        loadingInProgress = false;
    	refreshPanel();
    	validateInput();
    	sync();
    	
    	
    	
    	/*
        StringBuilder sbR0 = new StringBuilder();
        for (int i=0; i<R0Model.getColumnCount(); i++) {
            if (i>0)
                sbR0.append(" ");

            if (R0Model.getValueAt(0, i) != null)
                sbR0.append(R0Model.getValueAt(0, i));
            else
                sbR0.append("2.0");
        }
        bdmm.R0.get().setDimension(R0Model.getColumnCount());
        bdmm.R0.get().valuesInput.setValue(
                sbR0.toString(),
                bdmm.R0.get());

        StringBuilder sbdelta = new StringBuilder();
        for (int i=0; i<deltaModel.getColumnCount(); i++) {
            if (i>0)
                sbdelta.append(" ");

            if (deltaModel.getValueAt(0, i) != null)
                sbdelta.append(deltaModel.getValueAt(0, i));
            else
                sbdelta.append("1.0");
        }
        bdmm.becomeUninfectiousRate.get().setDimension(deltaModel.getColumnCount());
        bdmm.becomeUninfectiousRate.get().valuesInput.setValue(
                sbdelta.toString(),
                bdmm.becomeUninfectiousRate.get());

        StringBuilder sbsampling = new StringBuilder();
        for (int i=0; i<samplingModel.getColumnCount(); i++) {
            if (i>0)
                sbsampling.append(" ");

            if (samplingModel.getValueAt(0, i) != null)
                sbsampling.append(samplingModel.getValueAt(0, i));
            else
                sbsampling.append("0.0 0.01");
        }
        bdmm.samplingProportion.get().setDimension(samplingModel.getColumnCount());
        bdmm.samplingProportion.get().valuesInput.setValue(
                sbsampling.toString(),
                bdmm.samplingProportion.get());

        StringBuilder sbsamplingtimes = new StringBuilder();
        for (int i=0; i<samplingTimesModel.getColumnCount(); i++) {
            if (i==0)
                sbsamplingtimes.append("0.0 ");

            if (i>0)
                sbsamplingtimes.append(" ");

            if (samplingTimesModel.getValueAt(0, i) != null)
                sbsamplingtimes.append(samplingTimesModel.getValueAt(0, i));
        }
        bdmm.samplingRateChangeTimesInput.get().setDimension(samplingTimesModel.getColumnCount()+1);
        bdmm.samplingRateChangeTimesInput.get().valuesInput.setValue(
                sbsamplingtimes.toString(),
                bdmm.samplingRateChangeTimesInput.get());


        StringBuilder sbRateMatrix = new StringBuilder();
        boolean first = true;
        for (int i=0; i<rateMatrixModel.getRowCount(); i++) {
            for (int j=0; j<rateMatrixModel.getColumnCount(); j++) {
                if (i == j)
                    continue;

                if (first)
                    first = false;
                else
                    sbRateMatrix.append(" ");

                if (rateMatrixModel.getValueAt(i, j) != null)
                    sbRateMatrix.append(rateMatrixModel.getValueAt(i, j));
                else
                    sbRateMatrix.append("0.1");
            }
        }
        bdmm.migrationMatrix.get().setDimension(
            R0Model.getColumnCount()*(R0Model.getColumnCount()-1));
        bdmm.migrationMatrix.get().valuesInput.setValue(
            sbRateMatrix.toString(),
            bdmm.migrationMatrix.get());

        bdmm.R0.get().isEstimatedInput.setValue(
            R0EstCheckBox.isSelected(), bdmm.R0.get());
        bdmm.migrationMatrix.get().isEstimatedInput.setValue(
            rateMatrixEstCheckBox.isSelected(), bdmm.migrationMatrix.get());
        bdmm.samplingProportion.get().isEstimatedInput.setValue(
                samplingEstCheckBox.isSelected(), bdmm.samplingProportion.get());

        try {
            bdmm.R0.get().initAndValidate();
            bdmm.samplingProportion.get().initAndValidate();
            bdmm.samplingRateChangeTimesInput.get().initAndValidate();
            bdmm.becomeUninfectiousRate.get().initAndValidate();
            bdmm.migrationMatrix.get().initAndValidate();
            bdmm.initAndValidate();
        } catch (Exception ex) {
            System.err.println(ex.getCause());
            System.err.println("Error updating tree prior.");
        }

        refreshPanel();
        Platform.runLater(() ->
    		init(m_input, m_beastObject, itemNr, ExpandOption.TRUE, m_bAddButtons)
		);
		*/
        
        
        
        
    }
}
