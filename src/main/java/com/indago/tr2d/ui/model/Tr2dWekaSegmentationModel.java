/**
 *
 */
package com.indago.tr2d.ui.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.indago.data.segmentation.SegmentationMagic;
import com.indago.data.segmentation.SilentWekaSegmenter;
import com.indago.io.DataMover;
import com.indago.tr2d.projectfolder.ProjectData;
import com.indago.util.converter.RealDoubleThresholdConverter;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import ij.IJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * @author jug
 */
public class Tr2dWekaSegmentationModel {

	private final String FILENAME_THRESHOLD_VALUES = "thresholdValues.csv";
	private final String FILENAME_CLASSIFIERS = "classifierFilenames.csv";
	private final String FILENAME_PREFIX_SUM_IMGS = "sumImage";
	private final String FILENAME_PREFIX_CLASSIFICATION_IMGS = "classificationImage";

	private final Tr2dModel model;

	private SilentWekaSegmenter< DoubleType > segClassifier;

	private List< Double > listThresholds = new ArrayList< Double >();
	private List< String > listClassifierFilenames = new ArrayList< String >();

	private RandomAccessibleInterval< DoubleType > imgClassification = null;
	private RandomAccessibleInterval< DoubleType > imgSegmentHypotheses = null;

	private final File dataFolder;

	/**
	 *
	 */
	public Tr2dWekaSegmentationModel( final Tr2dModel model ) {
		this.model = model;
		final CsvParser parser = new CsvParser( new CsvParserSettings() );

		dataFolder = new File( ProjectData.WEKA_SEGMENTATION_FOLDER.getAbsolutePathIn( model.getProjectFolderBasePath() ) );
		if ( !dataFolder.exists() ) {
			dataFolder.mkdirs();
		}

		final File thresholdValues = new File( dataFolder, FILENAME_THRESHOLD_VALUES );
		try {
			final List< String[] > rows = parser.parseAll( new FileReader( thresholdValues ) );
			for ( final String[] strings : rows ) {
				for ( final String value : strings ) {
					try {
						listThresholds.add( Double.parseDouble( value ) );
					} catch ( final NumberFormatException e ) {
						System.err.println( "Could not parse threshold value: " + value );
					} catch ( final Exception e ) {
					}
				}

			}
		} catch ( final FileNotFoundException e ) {
			listThresholds.add( 0.5 );
			listThresholds.add( 0.7 );
			listThresholds.add( 0.95 );
		}

		final File classifierFilenames = new File( dataFolder, FILENAME_CLASSIFIERS );
		try {
			final List< String[] > rows = parser.parseAll( new FileReader( classifierFilenames ) );
			for ( final String[] strings : rows ) {
				for ( final String value : strings ) {
					listClassifierFilenames.add( value );
				}

			}
		} catch ( final FileNotFoundException e ) {
		}

		// Try to load classification and sum images corresponding to given classifiers
		int i = 0;
		for ( final String string : listClassifierFilenames ) {
			i++;
			System.out.println(
					"Would load persistent classification and sum images here (if data handlig would not be a f****** mess in the ImageJ universe..." );
//			final ImagePlus imgPlusClassificaiton =
//					IJ.openImage( new File( dataFolder, FILENAME_PREFIX_CLASSIFICATION_IMGS + i + ".tif" ).getAbsolutePath() );
//			imgClassification = ImageJFunctions.wrap( imgPlusClassificaiton );
//			final ImagePlus imgPlusSumImage =
//					IJ.openImage( new File( dataFolder, FILENAME_PREFIX_CLASSIFICATION_IMGS + i + ".tif" ).getAbsolutePath() );
//			imgSegmentHypotheses = ImageJFunctions.wrap( imgPlusSumImage );
		}
	}

	/**
	 * Loads the given classifier file.
	 *
	 * @param classifierFile
	 */
	private void loadClassifier( final File classifierFile ) {
		SegmentationMagic
				.setClassifier( classifierFile.getParent() + "/", classifierFile.getName() );
		segClassifier = SegmentationMagic.getClassifier();
	}

	/**
	 * @return
	 */
	public List< String > getClassifierFilenames() {
		return listClassifierFilenames;
	}

	/**
	 * @param absolutePath
	 */
	public void setClassifierPaths( final List< String > absolutePaths ) throws IllegalArgumentException {
		this.listClassifierFilenames = absolutePaths;

		try {
			final FileWriter writer = new FileWriter( new File( dataFolder, FILENAME_CLASSIFIERS ) );
			for ( final String string : absolutePaths ) {
				writer.append( string );
				writer.append( "\n" );
			}
			writer.flush();
			writer.close();
		} catch ( final IOException e ) {
			e.printStackTrace();
		}
	}

	/**
	 * Performs the segmentation procedure previously set up.
	 */
	public void segment() {
		int i = 0;
		//TODO classified images have to be joined to one labeling!!!
		for ( final String absolutePath : listClassifierFilenames ) {
			i++;
			System.out.println( String.format("Classifier %d of %d -- %s", i, listClassifierFilenames.size(), absolutePath) );

			final File cf = new File( absolutePath );
			if ( !cf.exists() || !cf.canRead() )
				System.err.println( String.format( "Given classifier file cannot be read (%s)", absolutePath ) );
			loadClassifier( cf );

    		// classify frames
    		imgClassification = SegmentationMagic.returnClassification( model.getImgOrig() );
			IJ.save(
					ImageJFunctions.wrap( imgClassification, "classification image" ).duplicate(),
					new File( dataFolder, FILENAME_PREFIX_CLASSIFICATION_IMGS + i + ".tif" ).getAbsolutePath() );

    		// collect thresholds in SumImage
    		RandomAccessibleInterval< DoubleType > imgTemp;
    		imgSegmentHypotheses =
    				DataMover.createEmptyArrayImgLike( imgClassification, new DoubleType() );
    		for ( final Double d : listThresholds ) {
    			imgTemp = Converters.convert(
    					imgClassification,
    					new RealDoubleThresholdConverter( d ),
    					new DoubleType() );
    			DataMover.add( imgTemp, imgSegmentHypotheses );
    		}
			IJ.save(
					ImageJFunctions.wrap( imgSegmentHypotheses, "sum image" ).duplicate(),
					new File( dataFolder, FILENAME_PREFIX_SUM_IMGS + i + ".tif" ).getAbsolutePath() );
		}
	}

	/**
	 * @return
	 * @throws IllegalAccessException
	 */
	public RandomAccessibleInterval< DoubleType > getClassification() {
		return imgClassification;
	}

	/**
	 * @return
	 * @throws IllegalAccessException
	 */
	public RandomAccessibleInterval< DoubleType > getSegmentHypotheses() {
		return imgSegmentHypotheses;
	}

	/**
	 * @return the listThresholds
	 */
	public List< Double > getListThresholds() {
		return listThresholds;
	}

	/**
	 *
	 * @param list
	 */
	public void setListThresholds( final List< Double > list ) {
		this.listThresholds = list;
		try {
			final FileWriter writer = new FileWriter( new File( dataFolder, FILENAME_THRESHOLD_VALUES ) );
			for ( final Double value : listThresholds ) {
				writer.append( value.toString() );
				writer.append( ", " );
			}
			writer.flush();
			writer.close();
		} catch ( final IOException e ) {
			e.printStackTrace();
		}
	}
}
