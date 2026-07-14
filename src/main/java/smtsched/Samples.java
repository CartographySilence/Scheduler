package smtsched;

/** Built-in specs, used when no file is given. */
final class Samples {

    private Samples() {}

    static final String PIPELINE = """
        {
          "processors": 1,
          "tasks": [
            { "id": "boot",      "duration": 3,  "deadline": 3 },
            { "id": "auth",      "duration": 4,  "deadline": 8,   "after": ["boot"] },
            { "id": "loadCfg",   "duration": 2,  "deadline": 6,   "after": ["boot"] },
            { "id": "indexA",    "duration": 6,  "deadline": 10 },
            { "id": "indexB",    "duration": 6,  "deadline": 10 },
            { "id": "indexC",    "duration": 6,  "deadline": 10 },
            { "id": "indexD",    "duration": 6,  "deadline": 12 },
            { "id": "query",     "duration": 5,  "deadline": 18,  "after": ["auth", "indexA"] },
            { "id": "aggregate", "duration": 4,  "deadline": 20,  "after": ["indexB", "indexC"] },
            { "id": "rank",      "duration": 3,  "deadline": 22,  "after": ["query", "aggregate"] },
            { "id": "render",    "duration": 4,  "deadline": 24,  "after": ["rank"] },
            { "id": "audit",     "duration": 5,  "deadline": 14 },
            { "id": "backup",    "duration": 7,  "deadline": 16 },
            { "id": "notify",    "duration": 2,  "deadline": 28,  "after": ["render"] }
          ]
        }
        """;
}