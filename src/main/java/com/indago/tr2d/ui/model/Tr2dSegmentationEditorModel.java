package com.indago.tr2d.ui.model;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.labkit.labeling.Labeling;
import net.imglib2.labkit.labeling.LabelingSerializer;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.GenericComposite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Tr2dSegmentationEditorModel {

	private final Tr2dModel model;

	private ImgLabeling< String, IntType > segmentations = null;

	public Tr2dSegmentationEditorModel(Tr2dModel model) {
		this.model = model;
	}

	public List< RandomAccessibleInterval< IntType > > getSumImages() {
		if(segmentations == null)
			return model.getSegmentationModel().getSumImages();
		else
			return toListOfBitmaps(segmentations);
	}

	static List<RandomAccessibleInterval<IntType>> toListOfBitmaps(ImgLabeling< String, ? > segmentations) {
		List<String> labels = new ArrayList<>( segmentations.getMapping().getLabels() );
		labels.sort(String::compareTo);
		return labels.stream().map(label -> toBitmap(segmentations, label)).collect(
				Collectors.toList());
	}

	static RandomAccessibleInterval<IntType> toBitmap(
			ImgLabeling< String, ? > segmentations, String label)
	{
		return Converters.convert((RandomAccessibleInterval<LabelingType<String> >) segmentations,
				(in, out) -> out.set(in.contains(label) ? 1 : 0), new IntType());
	}

	public ImgLabeling< String, IntType > asLabeling()
	{
		List<RandomAccessibleInterval<IntType>> images = model.getSegmentationModel().getSumImages();
		List<Labeling> labelings = new ArrayList<>();
		for(int i = 0; i < images.size(); i++)
			labelings.add(toLabeling("Segmentation " + (i + 1) + " ",
					images.get(i)));
		segmentations = joinLabelings(labelings);
		return segmentations;
	}

	private ImgLabeling< String, IntType > joinLabelings(
			List< ? extends RandomAccessibleInterval< Set<String> > > labelings)
	{
		RandomAccessibleInterval< Set< String > > stack =
				Views.stack(labelings);
		RandomAccessibleInterval< ? extends GenericComposite< Set< String > > >
				collapsed = Views.collapse(stack);
		ArrayImg< IntType, IntArray > result =
				ArrayImgs.ints(Intervals.dimensionsAsLongArray(collapsed));
		ImgLabeling<String, IntType> labeling = new ImgLabeling<>(result);
		LoopBuilder.setImages(collapsed, labeling).forEachPixel(
				(c, l) -> {
					for(int i = 0; i < labelings.size(); i++)
						l.addAll(c.get(i));
				}
		);
		return labeling;
	}

	private static Labeling toLabeling(String prefix, RandomAccessibleInterval< IntType > image) {
		int maxValue = max(image);
		List< Set< String > > labelSets = labelSets(prefix, maxValue);
		Img< IntType > intTypes = ImgView.wrap(image, null);
		ImgLabeling< String, ? > imgLabeling = LabelingSerializer
				.fromImageAndLabelSets(intTypes, labelSets);
		return new Labeling(labels(prefix, maxValue), imgLabeling);
	}

	private static List<Set<String>> labelSets(String prefix, int maxValue) {
		return IntStream.rangeClosed(0, maxValue).mapToObj(
				index -> labelSet(prefix, index)).collect(
				Collectors.toList());
	}

	private static Set<String> labelSet(String prefix, int index) {
		return IntStream.rangeClosed(1, index).mapToObj(i -> labelTitle(prefix, i)).collect(Collectors.toSet());
	}

	private static List<String> labels(String prefix, int maxValue) {
		return IntStream.rangeClosed(1, maxValue).mapToObj(
				x -> labelTitle(prefix, x)).collect(Collectors.toList());
	}

	private static String labelTitle(String prefix, int x) {
		return prefix + "Level " + x;
	}

	static int max(RandomAccessibleInterval<IntType> img) {
		IntType result = Util.getTypeFromInterval(img).copy();
		Views.iterable(img).forEach(pixel -> { if(pixel.getInteger() > result.getInteger()) result.set(pixel); } );
		return result.getInteger();
	}

	public Tr2dModel getModel() {
		return model;
	}
}
