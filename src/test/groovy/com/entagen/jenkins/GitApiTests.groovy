package com.entagen.jenkins

import org.junit.Test

class GitApiTests extends GroovyTestCase {

    @Test public void testEachResultLine_goodCommand() {
        GitApi gitApi = new GitApi()

        gitApi.eachResultLine("echo foo bar baz") { String line ->
            assert line == "foo bar baz"
        }
    }

    @Test public void testEachResultLine_badCommand() {
        GitApi gitApi = new GitApi()

        assert "Cannot run program \"mrmxyzptlk\": error=2, No such file or directory" == shouldFail {
            gitApi.eachResultLine("mrmxyzptlk") { String line ->
                fail("Should not have gotten here, this should throw an error as the command shouldn't exist")
            }
        }
    }

    @Test public void testBadCommandThrowsException() {
        GitApi gitApi = new GitApi()

        assert "Error executing command: cat thisfiledoesntexist -> cat: thisfiledoesntexist: No such file or directory" == shouldFail {
            gitApi.eachResultLine("cat thisfiledoesntexist") { String line ->
                fail("Should not have gotten here, this should throw an error, the command exists, but it doesn't run successfully")
            }
        }
    }

    @Test public void testGetBranchNames() {
        String mockResult = """
10b42258f451ebf2640d3c18850e0c22eecdad4\trefs/heads/ted/feature_branch
b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
abd856d2ae658ee5f14889b465f3adcaf65fb52b\trefs/heads/release_1.0rc1
garbage line that should be ignored
        """.trim()

        GitApi gitApi = new GitApiMockedResult(mockResult: mockResult)

        List<String> branchNames = gitApi.branchNames

        assert ["master", "release_1.0rc1", "ted/feature_branch"] == branchNames.sort()
    }

    @Test public void testGetFilteredBranchNames() {
        String mockResult = """
10b42258f451ebf2640d3c18850e0c22eecdad4\trefs/heads/feature/myfeature
b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
abd856d2ae658ee5f14889b465f3adcaf65fb52b\trefs/heads/release/1.0rc1
abd856d2ae658ee5f14889b465f3adcaf65fb52b\trefs/heads/other_branch
        """.trim()

        GitApi gitApi = new GitApiMockedResult(mockResult: mockResult)

        gitApi.branchNameFilter = ~/feature\/.+|release\/.+|master/
        List<String> branchNames = gitApi.branchNames

        assert ["feature/myfeature", "master", "release/1.0rc1"] == branchNames.sort()
    }

    @Test public void testGetFilteredBranchNames_singleFilter() {
        String mockResult = """
10b42258f451ebf2640d3c18850e0c22eecdad4\trefs/heads/feature/myfeature
b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
abd856d2ae658ee5f14889b465f3adcaf65fb52b\trefs/heads/release/1.0rc1
abd856d2ae658ee5f14889b465f3adcaf65fb52b\trefs/heads/other_branch
        """.trim()

        GitApi gitApi = new GitApiMockedResult(mockResult: mockResult, branchNameFilter: ~"feature/.+")

        List<String> branchNames = gitApi.branchNames

        assert ["feature/myfeature"] == branchNames.sort()
    }
}


class GitApiMockedResult extends GitApi {
    String mockResult = "mock result"

    @Override
    void eachResultLine(String command, Closure closure) {
        mockResult.eachLine { String line ->
            closure(line)
        }
    }
}
