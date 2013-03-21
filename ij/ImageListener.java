package ij;

	/** Plugins that implement this interface are notified when
		an image is opened, closed or updated. The 
		Plugins/Utilities/Monitor Events command uses this interface.
	*/
	public interface ImageListener {

	public void imageOpened(ImagePlus imp);

	public void imageClosed(ImagePlus imp);

	public void imageUpdated(ImagePlus imp);

}
