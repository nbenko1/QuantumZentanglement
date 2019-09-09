package edu.southwestern.tasks.interactive.picbreeder;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.aqwis.SimpleTiledZentangle;
import com.aqwis.models.SimpleTiledZentangleWFCModel;

import edu.southwestern.MMNEAT.MMNEAT;
import edu.southwestern.evolution.genotypes.Genotype;
import edu.southwestern.networks.Network;
import edu.southwestern.parameters.Parameters;
import edu.southwestern.tasks.interactive.InteractiveEvolutionTask;
import edu.southwestern.util.BooleanUtil;
import edu.southwestern.util.graphics.GraphicsUtil;
import edu.southwestern.util.random.RandomNumbers;

/**
 * Implementation of picbreeder that extends InteractiveEvolutionTask and uses
 * Java Swing components for graphical interface
 * 
 * Original Picbreeder paper: Jimmy Secretan, Nicholas Beato, David B.
 * D'Ambrosio, Adelein Rodriguez, Adam Campbell, Jeremiah T. Folsom-Kovarik and
 * Kenneth O. Stanley. Picbreeder: A Case Study in Collaborative Evolutionary
 * Exploration of Design Space. Evolutionary Computation 19, 3 (2011), 373-403.
 * DOI: http://dx.doi.org/10.1162/evco_a_00030
 * 
 * @author Lauren Gillespie
 * @author Isabel Tweraser
 *
 * @param <T>
 */
public class PicbreederTask<T extends Network> extends InteractiveEvolutionTask<T> {

	public static int runNumber = 0;

	public static final int CPPN_NUM_INPUTS = 4;
	public static final int CPPN_NUM_OUTPUTS = 3;

	private static final int ZENTANGLE_BUTTON_INDEX = -8;

	/**
	 * Default constructor
	 * 
	 * @throws IllegalAccessException
	 */
	public PicbreederTask() throws IllegalAccessException {
		super();

		// A check box that switches the output between colorful and black/white
		JCheckBox blackAndWhite = new JCheckBox("black&white",
				Parameters.parameters.booleanParameter("blackAndWhitePicbreeder"));
		blackAndWhite.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("Flip Black/White");
				// Switch to opposite of current setting
				Parameters.parameters.changeBoolean("blackAndWhitePicbreeder");
				// Need to change all images and re-load
				resetButtons(true);
			}
		});

		// A check box that switches the output between two brightness levels and
		// continuous
		JCheckBox stark = new JCheckBox("stark", Parameters.parameters.booleanParameter("starkPicbreeder"));
		stark.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("Flip Stark Brightness");
				// Switch to opposite of current setting
				Parameters.parameters.changeBoolean("starkPicbreeder");
				// Need to change all images and re-load
				resetButtons(true);
			}
		});

		JPanel imageTweaks = new JPanel();
		imageTweaks.setLayout(new GridLayout(2, 1, 2, 2));
		imageTweaks.add(blackAndWhite);
		imageTweaks.add(stark);
		top.add(imageTweaks);

		// Add the Zentangle button
		ImageIcon zentangle = new ImageIcon("data\\picbreeder\\zentangle.png");
		Image zentangle2 = zentangle.getImage().getScaledInstance(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT, 1);
		JButton zentangleButton = new JButton(new ImageIcon(zentangle2));
		zentangleButton.setPreferredSize(new Dimension(ACTION_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT));
		zentangleButton.setText("Zentangle");
		zentangleButton.setName("" + ZENTANGLE_BUTTON_INDEX);
		zentangleButton.setToolTipText("Zentangle button");
		zentangleButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				zentangle();
			}

		});

		if (!Parameters.parameters.booleanParameter("simplifiedInteractiveInterface")) {
			top.add(zentangleButton);
		}
	}

	/**
	 * Save a single hi-res version of a particular image. This is used by the
	 * zentangle method below, though I'm not sure it belongs in this class, or even
	 * deserves its own method.
	 * 
	 * @param filename
	 * @param i
	 * @param dim
	 */
	public void saveSingle(String filename, int i, int dim) {
		// Use of imageHeight and imageWidth allows saving a higher quality image than
		// is on the button
		BufferedImage toSave1 = GraphicsUtil.imageFromCPPN((Network) scores.get(i).individual.getPhenotype(), dim, dim,
				inputMultipliers);
		String filename1 = filename + "1.bmp";
		GraphicsUtil.saveImage(toSave1, filename1);

		System.out.println("image " + filename1 + " was saved successfully");
	}

	/**
	 * Code from Sarah Friday, Anna Krolikowski, and Alice Quintanilla from their
	 * final Spring 2019 AI project.
	 * 
	 * Saves several selected images from picbreeder to disk and then runs the Wave
	 * Function Collapse code to make a Zentangle mosaic out of the results.
	 * 
	 * TODO: Need to clean this code up a bit
	 */
	public void zentangle() {
		// Make sure zentangle directory exists
		File d = new File("zentangle");
		if (!d.exists()) {
			d.mkdir();
		}
		
		int numSelected = this.selectedItems.size();
		if (!BooleanUtil.any(chosen) || numSelected <= 1) {
			System.out.println("Insufficient number of tiles chosen to zentangle.");
			JOptionPane.showMessageDialog(null, "Insufficient number of tiles chosen to zentangle. Select at least two.", "Information", JOptionPane.INFORMATION_MESSAGE);
		} else {
			runNumber++;
			String waveFunctionSaveLocation = SimpleTiledZentangle.getSaveDirectory() + "/";
			File dir = new File(waveFunctionSaveLocation);
			if (!dir.exists()) { // Create save directory if it does not exist
				dir.mkdir();
			}

			String[] tileNames = new String[scores.size()];
			int numSaved = 0;
			int numStored = 0;
			int backgroundSize = 1440; // Hard coded image size: TODO: Use param
			int tileSize = 48; // Hard coded: TODO: param

			// Pick two random distinct indices to determine which images make up background patterns
			int[] bgIndices = RandomNumbers.randomDistinct(2, numSelected);			
			int bgIndex1 = bgIndices[0];
			int bgIndex2 = bgIndices[1];

			for (int i = 0; i < numSelected; i++) {
				if (i == bgIndex1) {
					// Represents a template pattern
					saveSingle(waveFunctionSaveLocation + "background", this.selectedItems.get(i), backgroundSize);
					if (numSelected < 3) { // If there are only two images, one serves as a background pattern AND a tile pattern
						String fullName = "tile" + numSaved + "_";
						tileNames[numStored++] = fullName + "1";
						saveSingle(waveFunctionSaveLocation + fullName, this.selectedItems.get(i), tileSize);
						numSaved++;
					}
				} else if (i == bgIndex2) {
					// A possible second template pattern
					saveSingle(waveFunctionSaveLocation + "background2", this.selectedItems.get(i), backgroundSize);
					String fullName = "tile" + numSaved + "_";
					tileNames[numStored++] = fullName + "1";
					saveSingle(waveFunctionSaveLocation + fullName, this.selectedItems.get(i), tileSize);
					numSaved++;
				} else {
					// All other images used to create background tiles with WFC
					String fullName = "tile" + numSaved + "_";
					tileNames[numStored++] = fullName + "1";
					saveSingle(waveFunctionSaveLocation + fullName, this.selectedItems.get(i), tileSize);
					numSaved++;
				}
			}

			// At this point, tileNames only stores tile images that will be used with WFC, though the array has
			// some empty slots at then end which are null.
			
			// use wfc to create final zentangle image, save it as zentangle.bmp

			int numPartitions = 2;
			int standardSize = numStored / numPartitions;
			ArrayList<String> tilesToProcess = new ArrayList<>();
			int zentangleNumber = 1;
			for (int i = 0; i < numStored; i++) {
				tilesToProcess.add(tileNames[i]);
				// The partition is full, create a zentangle with WFC
				if (this.selectedItems.size() <= 5 || (i + 1) % standardSize == 0) {
					// Writes data.xml
					SimpleTiledZentangleWFCModel
							.writeAdjacencyRules(tilesToProcess.toArray(new String[tilesToProcess.size()]));
					// data.xml gets read in this next method
					try {
						SimpleTiledZentangle.simpleTiledZentangle(zentangleNumber++);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					tilesToProcess.clear(); // Empty out partition
				}
			}

			BufferedImage bgImage1 = null;
			BufferedImage bgImage2 = null;
			BufferedImage firstImage = null;
			BufferedImage secondImage = null;
			BufferedImage thirdImage = null;
			BufferedImage fourthImage = null;
			BufferedImage zentangle = null;
			try {
				bgImage1 = ImageIO.read(new File(waveFunctionSaveLocation + "/background1.bmp"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				bgImage2 = ImageIO.read(new File(waveFunctionSaveLocation + "/background21.bmp"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				firstImage = ImageIO.read(new File(waveFunctionSaveLocation + "/picbreederZentangle" + 1 + ".jpg"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				secondImage = ImageIO.read(new File(waveFunctionSaveLocation + "/picbreederZentangle" + 2 + ".jpg"));
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (numSaved == 3) {
				try {
					thirdImage = ImageIO.read(new File(waveFunctionSaveLocation + "/picbreederZentangle" + 3 + ".jpg"));
				} catch (IOException e) {
					e.printStackTrace();
				}
				zentangle = GraphicsUtil.zentangleImages(bgImage1, bgImage2, firstImage, secondImage, thirdImage);
			} else if (numSaved == 4) {
				try {
					thirdImage = ImageIO.read(new File(waveFunctionSaveLocation + "/picbreederZentangle" + 3 + ".jpg"));
					fourthImage = ImageIO
							.read(new File(waveFunctionSaveLocation + "/picbreederZentangle" + 4 + ".jpg"));
				} catch (IOException e) {
					e.printStackTrace();
				}
				zentangle = GraphicsUtil.zentangleImages(bgImage1, bgImage2, secondImage, thirdImage, fourthImage);
			} else {
				zentangle = GraphicsUtil.zentangleImages(bgImage1, firstImage, secondImage);
			}
			File outputfile = new File(waveFunctionSaveLocation + "/zentangle.png");
			try {
				ImageIO.write(zentangle, "png", outputfile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			System.out.println("image was saved successfully");

			try {
				Desktop.getDesktop().open(outputfile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	/**
	 * X and Y input labels, distance from center is useful for radial distance,
	 * bias is required for all neural networks.
	 */
	@Override
	public String[] sensorLabels() {
		return new String[] { "X-coordinate", "Y-coordinate", "distance from center", "bias" };
	}

	/**
	 * Hue, saturation, and brightness values being output by CPPN
	 */
	@Override
	public String[] outputLabels() {
		return new String[] { "hue-value", "saturation-value", "brightness-value" };
	}

	/**
	 * Window title
	 */
	@Override
	protected String getWindowTitle() {
		return "Picbreeder";
	}

	/**
	 * Create BufferedImage from CPPN
	 */
	@Override
	protected BufferedImage getButtonImage(T phenotype, int width, int height, double[] inputMultipliers) {
		return GraphicsUtil.imageFromCPPN(phenotype, width, height, inputMultipliers);
	}

	/**
	 * No additional behavior of click other than initial response is used
	 */
	@Override
	protected void additionalButtonClickAction(int scoreIndex, Genotype<T> individual) {
		// Do nothing
	}

	/**
	 * Save generated images from CPPN
	 */
	@Override
	protected void save(String filename, int i) {
		// Use of imageHeight and imageWidth allows saving a higher quality image than
		// is on the button
		BufferedImage toSave = GraphicsUtil.imageFromCPPN((Network) scores.get(i).individual.getPhenotype(),
				Parameters.parameters.integerParameter("imageWidth"),
				Parameters.parameters.integerParameter("imageHeight"), inputMultipliers);
		filename += ".bmp";
		GraphicsUtil.saveImage(toSave, filename);
		System.out.println("image " + filename + " was saved successfully");
	}

	/**
	 * Returns the number of inputs used in the interactive evolution task
	 */
	@Override
	public int numCPPNInputs() {
		return CPPN_NUM_INPUTS;
	}

	/**
	 * Returns the number of outputs used in the interactive evolution task
	 */
	@Override
	public int numCPPNOutputs() {
		return CPPN_NUM_OUTPUTS;
	}

	/**
	 * For quick testing
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			MMNEAT.main(new String[] { "runNumber:0", "randomSeed:0", "trials:1", "mu:16", "maxGens:500", "io:false",
					"netio:false", "mating:true", "fs:false",
					"task:edu.southwestern.tasks.interactive.picbreeder.PicbreederTask", "allowMultipleFunctions:true",
					"ftype:0", "watch:false", "netChangeActivationRate:0.3", "cleanFrequency:-1",
					"simplifiedInteractiveInterface:false", "recurrency:false", "saveAllChampions:true",
					"cleanOldNetworks:false", "ea:edu.southwestern.evolution.selectiveBreeding.SelectiveBreedingEA",
					"imageWidth:2000", "imageHeight:2000", "imageSize:200", "includeFullSigmoidFunction:true",
					"includeFullGaussFunction:true", "includeCosineFunction:true", "includeGaussFunction:false",
					"includeIdFunction:true", "includeTriangleWaveFunction:false", "includeSquareWaveFunction:false",
					"includeFullSawtoothFunction:false", "includeSigmoidFunction:false", "includeAbsValFunction:false",
					"includeSawtoothFunction:false" });
		} catch (FileNotFoundException | NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns type of file being saved (BMP)
	 */
	@Override
	protected String getFileType() {
		return "BMP Images";
	}

	/**
	 * Returns extension of saved images (.bmp)
	 */
	@Override
	protected String getFileExtension() {
		return "bmp";
	}
}
