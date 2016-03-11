package nctu.nol.algo;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;


public class PeakDetector {
	private static final String TAG = "PeakDetector";
	private float WINDOWSIZE_IN_MILLISECOND;
	private float PEAKWINDOWSIZE_IN_MILLISECOND;
	
	//Constructor
	public PeakDetector(float w_size, float p_size){
		this.WINDOWSIZE_IN_MILLISECOND = w_size;
		this.PEAKWINDOWSIZE_IN_MILLISECOND = p_size;
	}
	
	//Check the rule
	private boolean IsPeak(final float []vals, int index, float threshold){
		if(index < 1 || index > vals.length-2)
			return false;	
		
		float prev = vals[index-1], 
			  cur = vals[index], 
			  next = vals[index+1];
		
		//�T�{�O�_�ŦX�W�h ( ������I��ۤv�p, �B�j��Threshold )
		if( cur > threshold && cur > prev && cur > next)
			return true;
		else
			return false;
	}
	
	//Check if there is another higher peak in Peak Window
	private int CheckNeighborPeak(final float[] attrs, final float[] vals, final int curPeakIndex ){
		int newPeak = -1;
		float maxVal = vals[curPeakIndex];
		//�V����
		for(int i = curPeakIndex-1; attrs[curPeakIndex]-attrs[i] <= PEAKWINDOWSIZE_IN_MILLISECOND && i > 0; i--){
			if( IsPeak(vals, i, maxVal) ){
				newPeak = i;
				maxVal = vals[newPeak];
			}
		}
		
		//�V�k��
		for(int i = curPeakIndex+1; attrs[i]-attrs[curPeakIndex] <= PEAKWINDOWSIZE_IN_MILLISECOND && i < vals.length-1; i++){
			if( IsPeak(vals, i, maxVal) ){
				newPeak = i;
				maxVal = vals[newPeak];
			}
		}
		
		//Recursive Check
		if(newPeak != -1){
			int temp = CheckNeighborPeak(attrs, vals, newPeak);
			if(temp != -1)
				newPeak = temp;
		}
		return newPeak;
	}
	
	public final List<Integer> findPeakIndex(final float[] attrs, final float[] vals, final float Threshold){
		if(attrs.length != vals.length){
			Log.e(TAG,"Time array length is not equal to vals array.");
			return null;
		}
			
		int curPos = 0, endPos = 0;
		List<Integer> peakIndex = new ArrayList<Integer>();
		
		while(endPos < attrs.length){
			//���w����Window���k�ݦ�m
			while( endPos < attrs.length && attrs[endPos]-attrs[curPos] <= WINDOWSIZE_IN_MILLISECOND )
				endPos++;
			
			float maxVal = Float.NEGATIVE_INFINITY;
			int curPeakIndex = -1;
			
			// First Stage: �������I��ۤv�p, �B�j��Threshold �M maxVal ���I
			for(int i = curPos; i < endPos; i++){
				float t = (Threshold > maxVal)? Threshold : maxVal;
				
				//�ˬd�O�_�ŦX�i�p�W�h
				if( IsPeak(vals, i, t) ){

					//�T�{�P�W�@�Ӫi�p�۹j�@��Peak Window�H�W
					if(peakIndex.size() != 0){
						int prevPeakIndex = peakIndex.get(peakIndex.size()-1);
						if(Math.abs(attrs[i]-attrs[prevPeakIndex]) <= PEAKWINDOWSIZE_IN_MILLISECOND )
							continue;
					}
					maxVal = vals[i];
					curPeakIndex = i;
				}
					
			}
			
			//Second Stage: ��WINDOW�������i�p, �T�{peak window���O�_�٦���L�󰪪��i�p(���i���Window������)
			if(curPeakIndex != -1){
				int temp = CheckNeighborPeak(attrs,vals,curPeakIndex);
				if(temp != -1)
					curPeakIndex = temp;
				
				peakIndex.add(curPeakIndex);
			}
			
			curPos = endPos;
		}	
		return peakIndex;
	}
}
