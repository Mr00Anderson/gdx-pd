//////////////////////////////////////////////////////////////////////////////
//	Copyright 2011 Alex Leffelman
//	
//	Licensed under the Apache License, Version 2.0 (the "License");
//	you may not use this file except in compliance with the License.
//	You may obtain a copy of the License at
//	
//	http://www.apache.org/licenses/LICENSE-2.0
//	
//	Unless required by applicable law or agreed to in writing, software
//	distributed under the License is distributed on an "AS IS" BASIS,
//	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//	See the License for the specific language governing permissions and
//	limitations under the License.
//////////////////////////////////////////////////////////////////////////////

package net.mgsx.midi.sequence;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

import net.mgsx.midi.sequence.event.MidiEvent;
import net.mgsx.midi.sequence.util.MidiUtil;

public class MidiSequence
{
    public static final int HEADER_SIZE = 14;
    public static final byte[] IDENTIFIER = { 'M', 'T', 'h', 'd' };

    public static final int DEFAULT_RESOLUTION = 480;

    private int mType;
    private int mTrackCount;
    private int mResolution;

    private ArrayList<MidiTrack> mTracks;

    public MidiSequence()
    {
        this(DEFAULT_RESOLUTION);
    }

    public MidiSequence(int resolution)
    {
        this(resolution, new ArrayList<MidiTrack>());
    }

    public MidiSequence(int resolution, ArrayList<MidiTrack> tracks)
    {
        mResolution = resolution >= 0 ? resolution : DEFAULT_RESOLUTION;

        mTracks = tracks != null ? tracks : new ArrayList<MidiTrack>();
        mTrackCount = tracks.size();
        mType = mTrackCount > 1 ? 1 : 0;
    }

    @Deprecated
    public MidiSequence(File fileIn) throws FileNotFoundException, IOException
    {
        this(new FileInputStream(fileIn));
    }
    
    public MidiSequence(FileHandle file)
    {
    	this(file.read());
    }

    public MidiSequence(InputStream rawIn)
    {
        BufferedInputStream in = new BufferedInputStream(rawIn);
        try{
	        byte[] buffer = new byte[HEADER_SIZE];
	        in.read(buffer);
	
	        initFromBuffer(buffer);
	
	        mTracks = new ArrayList<MidiTrack>();
	        for(int i = 0; i < mTrackCount; i++)
	        {
	            mTracks.add(new MidiTrack(in));
	        }
        }catch(IOException e){
        	throw new GdxRuntimeException(e);
        }
    }
    
    public void setType(int type)
    {
        if(type < 0)
        {
            type = 0;
        }
        else if(type > 2)
        {
            type = 1;
        }
        else if(type == 0 && mTrackCount > 1)
        {
            type = 1;
        }
        mType = type;
    }

    public int getType()
    {
        return mType;
    }

    public int getTrackCount()
    {
        return mTrackCount;
    }

    public void setResolution(int res)
    {
        if(res >= 0)
        {
            mResolution = res;
        }
    }

    public int getResolution()
    {
        return mResolution;
    }

    public long getLengthInTicks()
    {
        long length = 0;
        for(MidiTrack T : mTracks)
        {
            long l = T.getLengthInTicks();
            if(l > length)
            {
                length = l;
            }
        }
        return length;
    }

    public ArrayList<MidiTrack> getTracks()
    {
        return mTracks;
    }

    public void addTrack(MidiTrack T)
    {
        addTrack(T, mTracks.size());
    }

    public void addTrack(MidiTrack T, int pos)
    {

        if(pos > mTracks.size())
        {
            pos = mTracks.size();
        }
        else if(pos < 0)
        {
            pos = 0;
        }

        mTracks.add(pos, T);
        mTrackCount = mTracks.size();
        mType = mTrackCount > 1 ? 1 : 0;
    }

    public void removeTrack(int pos)
    {
        if(pos < 0 || pos >= mTracks.size())
        {
            return;
        }
        mTracks.remove(pos);
        mTrackCount = mTracks.size();
        mType = mTrackCount > 1 ? 1 : 0;
    }

    public void writeToFile(File outFile) throws FileNotFoundException, IOException
    {
        FileOutputStream fout = new FileOutputStream(outFile);

        fout.write(IDENTIFIER);
        fout.write(MidiUtil.intToBytes(6, 4));
        fout.write(MidiUtil.intToBytes(mType, 2));
        fout.write(MidiUtil.intToBytes(mTrackCount, 2));
        fout.write(MidiUtil.intToBytes(mResolution, 2));

        for(MidiTrack T : mTracks)
        {
            T.writeToFile(fout);
        }

        fout.flush();
        fout.close();
    }

    private void initFromBuffer(byte[] buffer)
    {
        if(!MidiUtil.bytesEqual(buffer, IDENTIFIER, 0, 4))
        {
            System.out.println("File identifier not MThd. Exiting");
            mType = 0;
            mTrackCount = 0;
            mResolution = DEFAULT_RESOLUTION;
            return;
        }

        mType = MidiUtil.bytesToInt(buffer, 8, 2);
        mTrackCount = MidiUtil.bytesToInt(buffer, 10, 2);
        mResolution = MidiUtil.bytesToInt(buffer, 12, 2);
    }
    
    public <T extends MidiEvent> Array<T> findEvents(Array<T> events, Class<T> type)
    {
    	for(MidiTrack track : mTracks)
    	{
    		track.findEvents(events, type);
    	}
    	return events;
    }
}
