package ij;

/**
* An Adapter class for the ImageListener interface. Note ImageListenerAdapter
* supports notification when an ImagePlus gets saved.
* <p>
* With this adapter you need only override the methods that you require
* notification for.
* <p>
* TODO When ImageJ has a minimum support of Java8 consider updating
* ImageListener to provide default methods for all these callbacks as this will
* allow ImageListener interface to be used as mix-in class whereas Java single
* inheritance precludes that for this ImageListenerAdapter class.
*
* @author Michael Ellis
*/
public class ImageListenerAdapter implements ImageListener {

	@Override
	public void imageOpened(ImagePlus imp) {
	}

	@Override
	public void imageClosed(ImagePlus imp) {
	}

	@Override
	public void imageUpdated(ImagePlus imp) {
	}

	public void imageSaved(ImagePlus imp) {
	}

}
