package com.couchbase.cblite.support;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.couchbase.cblite.CBLDatabase;

import android.util.Log;

public class CBLSequenceMap {
	
	private TreeSet<Long> sequences;
	private long lastSequence;
	private List<String> values;
	private long firstValueSequence;
	
	public CBLSequenceMap() {
		sequences = new TreeSet<Long>();
		values = new ArrayList<String>(100);
		firstValueSequence = 1;
		lastSequence = 0;
	}

	public synchronized long addValue(String value) {
		sequences.add(++lastSequence);
		values.add(value);
		return lastSequence;
	}
	
	public synchronized void removeSequence(long sequence) {
		sequences.remove(sequence);
	}
	
	public synchronized boolean isEmpty() {
		return sequences.isEmpty();
	}
	
	public synchronized long getCheckpointedSequence() {
		long sequence = lastSequence;
		if(!sequences.isEmpty()) {
			sequence = sequences.first() - 1;
		}
		
		if(sequence > firstValueSequence) {
			// Garbage-collect inaccessible values:
			int numToRemove = (int)(sequence - firstValueSequence);
			for(int i = 0; i < numToRemove; i++) {
				values.remove(0);
			}
			firstValueSequence += numToRemove;
		}
		
		return sequence;
	}
	
	public synchronized String getCheckpointedValue() {
		int index = (int)(getCheckpointedSequence() - firstValueSequence);
		return (index >= 0) ? values.get(index) : null; 
	}
}
