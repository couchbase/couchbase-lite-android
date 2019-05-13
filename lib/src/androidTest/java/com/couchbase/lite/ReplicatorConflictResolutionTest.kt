//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test

class TestConflictResolver(private var resolver: (Conflict) -> Document?) : ConflictResolver {
    var winner: Document? = null

    override fun resolve(conflict: Conflict): Document? {
        winner = resolver(conflict)
        return winner
    }
}

class ReplicatorConflictResolutionTests : BaseReplicatorTest() {

    // !!! FIXME: implement
    @Ignore("feature not yet implemented")
    @Test
    fun testConflictHandlerRemoteWins() {
        val doc = MutableDocument("doc")
        doc.setString("species", "Tiger")
        db.save(doc)

        val target = DatabaseEndpoint(otherDB)
        var config = makeConfig(true, false, false, target)
        run(config, 0, null)

        // make changes to both documents from db and otherDB
        val doc1 = db.getDocument("doc")!!.toMutable()
        doc1.setString("name", "Hobbes")
        db.save(doc1)

        val doc2 = otherDB.getDocument("doc")!!.toMutable()
        doc2.setString("pattern", "striped")
        otherDB.save(doc2)

        // Pull:
        config = makeConfig(false, true, false, target)

        val resolver = TestConflictResolver() { conflict -> conflict.remoteDocument }
        config.conflictResolver = resolver
        run(config, 0, null)

        assertEquals(1, db.count)

        val savedDoc = db.getDocument("doc")!!

        val exp: Map<String, Any> = mapOf("species" to "Tiger", "pattern" to "striped")
        assertEquals(savedDoc, resolver.winner)
        assertEquals(2, savedDoc.toMap().size)
        assertEquals(exp.keys, savedDoc.toMap().keys)
    }

    // !!! FIXME: implement
    @Ignore("feature not yet implemented")
    @Test
    fun testConflictHandlerLocalWins() {
        val doc = MutableDocument("doc")
        doc.setString("species", "Tiger")
        db.save(doc)

        val target = DatabaseEndpoint(otherDB)
        var config = makeConfig(true, false, false, target)
        run(config, 0, null)

        // make changes to both documents from db and otherDB
        val doc1 = db.getDocument("doc")!!.toMutable()
        doc1.setString("name", "Hobbes")
        db.save(doc1)

        val doc2 = otherDB.getDocument("doc")!!.toMutable()
        doc2.setString("pattern", "striped")
        otherDB.save(doc2)

        // Pull:
        config = makeConfig(false, true, false, target)

        val resolver = TestConflictResolver() { conflict -> conflict.localDocument }
        config.conflictResolver = resolver
        run(config, 0, null)

        assertEquals(1, db.count)

        val savedDoc = db.getDocument("doc")!!

        val exp: Map<String, Any> = mapOf("species" to "Tiger", "name" to "Hobbes")
        assertEquals(savedDoc, resolver.winner)
        assertEquals(2, savedDoc.toMap().size)
        assertEquals(exp.keys, savedDoc.toMap().keys)
    }

    // !!! FIXME: implement
    @Ignore("feature not yet implemented")
    @Test
    fun testConflictHandlerNullDoc() {
        val doc = MutableDocument("doc")
        doc.setString("species", "Tiger")
        db.save(doc)

        val target = DatabaseEndpoint(otherDB)
        var config = makeConfig(true, false, false, target)
        run(config, 0, null)

        // make changes to both documents from db and otherDB
        val doc1 = db.getDocument("doc")!!.toMutable()
        doc1.setString("name", "Hobbes")
        db.save(doc1)

        val doc2 = otherDB.getDocument("doc")!!.toMutable()
        doc2.setString("pattern", "striped")
        otherDB.save(doc2)

        // Pull:
        config = makeConfig(false, true, false, target)

        val resolver = TestConflictResolver() { conflict -> null }
        config.conflictResolver = resolver
        run(config, 0, null)

        assertNull(resolver.winner)
        assertEquals(0, db.count)
        assertNull(db.getDocument("doc"))
    }

    // !!! FIXME: implement
    @Ignore("feature not yet implemented")
    @Test
    fun testConflictHandlerDeletedLocalWins() {
        val doc = MutableDocument("doc")
        doc.setString("species", "Tiger")
        db.save(doc)

        val target = DatabaseEndpoint(otherDB)
        var config = makeConfig(true, false, false, target)
        run(config, 0, null)

        // make changes to both documents from db and otherDB
        db.delete(db.getDocument("doc")!!)

        val doc2 = otherDB.getDocument("doc")!!.toMutable()
        doc2.setString("pattern", "striped")
        otherDB.save(doc2)

        // Pull:
        config = makeConfig(false, true, false, target)

        val resolver = TestConflictResolver() { conflict -> null }
        config.conflictResolver = resolver
        run(config, 0, null)

        assertNull(resolver.winner)
        assertEquals(0, db.count)
        assertNull(db.getDocument("doc"))
    }

    // !!! FIXME: implement
    @Ignore("feature not yet implemented")
    @Test
    fun testConflictHandlerDeletedRemoteWins() {
        val doc = MutableDocument("doc")
        doc.setString("species", "Tiger")
        db.save(doc)

        val target = DatabaseEndpoint(otherDB)
        var config = makeConfig(true, false, false, target)
        run(config, 0, null)

        // make changes to both documents from db and otherDB
        val doc1 = db.getDocument("doc")!!.toMutable()
        doc1.setString("name", "Hobbes")
        db.save(doc1)

        otherDB.delete(otherDB.getDocument("doc")!!)

        // Pull:
        config = makeConfig(false, true, false, target)

        val resolver = TestConflictResolver() { conflict -> null }
        config.conflictResolver = resolver
        run(config, 0, null)

        assertNull(resolver.winner)
        assertEquals(0, db.count)
        assertNull(db.getDocument("doc"))
    }
}
