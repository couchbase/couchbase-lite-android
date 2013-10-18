package com.couchbase.cblite.testapp.tests;

import android.test.AndroidTestCase;

import com.couchbase.cblite.support.CBLSequenceMap;

import junit.framework.Assert;

public class SequenceMap extends AndroidTestCase {
	
	public void testSequenceMap() {
		
		CBLSequenceMap map = new CBLSequenceMap();
		
		Assert.assertEquals(0, map.getCheckpointedSequence());
		Assert.assertEquals(null, map.getCheckpointedValue());
		Assert.assertTrue(map.isEmpty());
		
		Assert.assertEquals(1, map.addValue("one"));
		Assert.assertEquals(0, map.getCheckpointedSequence());
		Assert.assertEquals(null, map.getCheckpointedValue());
		Assert.assertTrue(!map.isEmpty());
		
		Assert.assertEquals(2, map.addValue("two"));
		Assert.assertEquals(0, map.getCheckpointedSequence());
		Assert.assertEquals(null, map.getCheckpointedValue());
		
		Assert.assertEquals(3, map.addValue("three"));
		Assert.assertEquals(0, map.getCheckpointedSequence());
		Assert.assertEquals(null, map.getCheckpointedValue());
		
		map.removeSequence(2);
		Assert.assertEquals(0, map.getCheckpointedSequence());
		Assert.assertEquals(null, map.getCheckpointedValue());
		
		map.removeSequence(1);
		Assert.assertEquals(2, map.getCheckpointedSequence());
		Assert.assertEquals("two", map.getCheckpointedValue());		
		
		Assert.assertEquals(4, map.addValue("four"));
		Assert.assertEquals(2, map.getCheckpointedSequence());
		Assert.assertEquals("two", map.getCheckpointedValue());
		
		map.removeSequence(3);
		Assert.assertEquals(3, map.getCheckpointedSequence());
		Assert.assertEquals("three", map.getCheckpointedValue());
		
		map.removeSequence(4);
		Assert.assertEquals(4, map.getCheckpointedSequence());
		Assert.assertEquals("four", map.getCheckpointedValue());
		Assert.assertTrue(map.isEmpty());
	}

}
