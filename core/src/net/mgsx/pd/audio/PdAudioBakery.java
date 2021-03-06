package net.mgsx.pd.audio;

import java.io.IOException;

import org.puredata.core.PdBase;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;

import net.mgsx.pd.Pd;
import net.mgsx.pd.PdConfiguration;
import net.mgsx.pd.patch.PdPatch;
import net.mgsx.pd.utils.PdRuntimeException;

/**
 * PdAudioBakery allows to bake some patch to waveform table at runtime.
 * First call {@link #addTask(FileHandle, String, int, float)} to schedule all baking you need. And then
 * call {@link #start(BakingListener)} to begin baking process. No tasks should be added during process.
 * baking is done in a separated thread but require Pd audio context to work, so it's advised to close all
 * your patch before baking and don't use any {@link PdAudio} methods during the process.
 * Baking process will inform your code on progression and when completed through the {@link BakingListener}.
 * 
 * Only mono sounds baking (1 channel) is supported for now.
 * 
 * @author mgsx
 *
 */
public class PdAudioBakery 
{
	/**
	 * Baking process listener.
	 * Both {@link #progress(float)} and {@link #complete()} methods are executed in
	 * GL Thread.
	 * @author mgsx
	 *
	 */
	public static interface BakingListener
	{
		/**
		 * called at start and after each completed tasks
		 * @param percents progression in percent.
		 */
		public void progress(float percents);
		
		/**
		 * Called when all tasks are completed.
		 */
		public void complete();
	}
	
	private static class Baking
	{
		FileHandle patchFile;
		String array;
		int sampleRate;
		float time;
		float [] data;
	}
	
	private final Array<Baking> pendingBakings = new Array<Baking>();
	private final ObjectMap<String, Baking> baked = new ObjectMap<String, Baking>();
	
	private Thread bakingThread;
	
	/**
	 * Add a new baking task.
	 * @param patchFile patch file to bake.
	 * @param array destination array to write to.
	 * @param sampleRate sample rate to use.
	 * @param time destination duration.
	 */
	public void addTask(FileHandle patchFile, String array, int sampleRate, float time){
		if(bakingThread != null){
			throw new GdxRuntimeException("addTask should only be called before baking process.");
		}
		Baking baking = new Baking();
		baking.patchFile = patchFile;
		baking.array = array;
		baking.sampleRate = sampleRate;
		baking.time = time;
		pendingBakings.add(baking);
	}
	
	private void dispatchProgress(final BakingListener listener, final float percent){
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				listener.progress(percent);
			}
		});
	}
	private void dispatchComplete(final BakingListener listener){
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				listener.complete();
			}
		});
	}
	
	private PdPatch openPatch(FileHandle file){
		try {
			int handle = PdBase.openPatch(file.path());
			return new PdPatch(handle);
		} catch (IOException e) {
			throw new PdRuntimeException("unable to open patch", e);
		}
	}
	
	private void closePatch(PdPatch patch){
		PdBase.closePatch(patch.getPdHandle());
	}
	
	/**
	 * Start the baking process.
	 * @param listener used to get processing progression.
	 */
	public void start(final BakingListener listener)
	{
		if(bakingThread != null){
			throw new GdxRuntimeException("start should only be called once.");
		}
		bakingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				int total = pendingBakings.size;
				int count = 0;
				dispatchProgress(listener, 0);
				Pd.audio.pause();
				while(pendingBakings.size > 0){
					Baking baking = pendingBakings.pop();
					
					PdPatch patchToBake = openPatch(baking.patchFile);
					PdBase.openAudio(0, 1, baking.sampleRate); // TODO support both mono and stereo ?
					PdBase.computeAudio(true);
					
					int frames = (int)(baking.time * baking.sampleRate);
					int samples = frames;
					baking.data = new float[samples];
					int ticks = samples / PdBase.blockSize();
					int perr = PdBase.process(ticks, new float[]{}, baking.data);
					if(perr != 0) Gdx.app.error("Pd", "process error ....");
					closePatch(patchToBake);
					
					if(PdConfiguration.remoteEnabled){
						Gdx.app.error("PdBaking", "Warning : enable to retrieve array size in remote mode, assume destination array is big enough");
					}else{
						int size = Pd.audio.arraySize(baking.array);
						if(baking.data.length > size){
							size = baking.data.length;
							Gdx.app.error("PdBaking", "Warning : destination array " + baking.array + " size too short (" + String.valueOf(size) + "), shrink baked data (" + String.valueOf(baking.data.length) + ")");
						}else if(baking.data.length < size){
							Gdx.app.error("PdBaking", "Warning : destination array " + baking.array + " size is bigger (" + String.valueOf(size) + ") than baked data (" + String.valueOf(baking.data.length) + "), clearing array to prevent dirty buffer.");
							float [] nullData = new float[size - baking.data.length];
							Pd.audio.writeArray(baking.array, baking.data.length, nullData, 0, nullData.length);
						}
						
					}
					Pd.audio.writeArray(baking.array, 0, baking.data, 0, baking.data.length);
					
					baked.put(baking.array, baking);
					
					count++;
					
					dispatchProgress(listener, 100 * (float) count / (float) total);
				}
				Pd.audio.resume();
				dispatchComplete(listener);
			}
		}, "PdAudioBakery");
		bakingThread.start();
	}
}
