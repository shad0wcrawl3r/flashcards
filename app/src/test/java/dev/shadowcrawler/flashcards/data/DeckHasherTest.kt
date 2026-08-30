package dev.shadowcrawler.flashcards.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckHasherTest {

    /**
     * Regression test for a bug where re-importing a shared copy of the built-in sample deck
     * created a duplicate: DatabaseInitializer never gave the sample deck a hash, so
     * DeckImporter's `findByHash` lookup could never match it against an incoming import.
     */
    @Test
    fun `sample deck hash matches a re-imported copy of itself`() {
        val sampleHash = DeckHasher.compute(
            SampleData.sampleDeck.name,
            SampleData.cardsForDeck(0).toHashable()
        )

        val json = Json { ignoreUnknownKeys = true }
        val bulk = json.decodeFromString(BulkImportPayload.serializer(), sharedBulkPayloadJson)
        val importedDeck = bulk.decks.first { it.deck.name == "System Samples" }
        val importedHash = DeckHasher.compute(importedDeck.deck.name, importedDeck.cards.toHashable())

        assertEquals(
            "Re-importing the exact built-in sample deck should hash identically so it's skipped as a duplicate",
            sampleHash,
            importedHash
        )
    }
}

// Captured from https://paste.rs/KOBvx, the payload that reproduced the duplicate-import bug.
private const val sharedBulkPayloadJson = """
{"decks":[{"deck":{"name":"System Samples","description":"Sample cards provided with the application.","source":"app://sample","tags":["sample","system"]},"cards":[{"type":"QA","question":"What port does HTTPS normally use?","answer":"443","explanation":"HTTPS normally uses TCP port 443.","metadata":{"topic":"Networking"}},{"type":"QA","question":"What does DNS stand for?","answer":"Domain Name System","difficulty":8,"explanation":"DNS translates domain names into IP addresses.","metadata":{"topic":"Networking"}},{"type":"QA","question":"What does CIDR stand for?","answer":"Classless Inter-Domain Routing","difficulty":15,"metadata":{"topic":"Networking"}},{"type":"MCQ","question":"Which HTTP status code means \"Not Found\"?","answer":"404","choices":["200","301","404","500"],"explanation":"404 means the server couldn't find the requested resource.","metadata":{"topic":"Networking"}},{"type":"MCQ","question":"Which layer of the OSI model do routers primarily operate at?","answer":"Network","choices":["Physical","Data Link","Network","Transport"],"difficulty":10,"explanation":"Routers forward packets based on IP addresses, which live at the Network layer.","metadata":{"topic":"Networking"}}]},{"deck":{"name":"Linux Fundamentals","description":"Core Linux concepts every sysadmin should know.","source":"sample-import","tags":["linux","sysadmin"]},"cards":[{"type":"QA","question":"What command shows the current working directory?","answer":"pwd","difficulty":2,"explanation":"pwd stands for 'print working directory'.","metadata":{"topic":"Shell"}},{"type":"QA","question":"What file contains user account information on a Linux system?","answer":"/etc/passwd","difficulty":6,"explanation":"It stores usernames, UIDs, GIDs, home directories, and default shells.","metadata":{"topic":"System"}},{"type":"MCQ","question":"Which command changes file permissions?","answer":"chmod","choices":["chown","chmod","chgrp","umask"],"difficulty":4,"explanation":"chmod changes read/write/execute permissions on a file or directory.","metadata":{"topic":"Permissions"}},{"type":"MCQ","question":"What does the 'grep -i' flag do?","answer":"Performs a case-insensitive search","choices":["Inverts the match","Performs a case-insensitive search","Counts matching lines","Searches recursively"],"explanation":"-i tells grep to ignore letter casing while matching.","metadata":{"topic":"Text Processing"}},{"type":"MCQ","question":"Which signal does 'kill -9' send to a process?","answer":"SIGKILL","choices":["SIGTERM","SIGHUP","SIGKILL","SIGINT"],"difficulty":7,"explanation":"SIGKILL forcibly terminates a process and cannot be caught or ignored.","metadata":{"topic":"Processes"}},{"type":"QA","question":"What command lists currently running processes?","answer":"ps","difficulty":3,"metadata":{"topic":"Processes"}}]}]}
"""
