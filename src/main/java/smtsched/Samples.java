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

        static final String BIG = """
        {
          "processors": 6,
          "tasks": [
            { "id": "boot",      "duration": 3, "deadline": 5 },
            { "id": "loadCfg",   "duration": 2, "deadline": 8,  "after": ["boot"] },
            { "id": "auth",      "duration": 4, "deadline": 12, "after": ["boot"] },
            { "id": "netInit",   "duration": 3, "deadline": 12, "after": ["boot"] },
            { "id": "diskInit",  "duration": 4, "deadline": 12, "after": ["boot"] },

            { "id": "fetch01",   "duration": 5, "deadline": 25, "after": ["netInit"] },
            { "id": "fetch02",   "duration": 6, "deadline": 25, "after": ["netInit"] },
            { "id": "fetch03",   "duration": 4, "deadline": 25, "after": ["netInit"] },
            { "id": "fetch04",   "duration": 5, "deadline": 25, "after": ["netInit"] },
            { "id": "fetch05",   "duration": 7, "deadline": 28, "after": ["netInit"] },
            { "id": "fetch06",   "duration": 3, "deadline": 28, "after": ["netInit"] },
            { "id": "fetch07",   "duration": 6, "deadline": 28, "after": ["netInit"] },
            { "id": "fetch08",   "duration": 4, "deadline": 28, "after": ["netInit"] },

            { "id": "parse01",   "duration": 4, "deadline": 40, "after": ["fetch01"] },
            { "id": "parse02",   "duration": 5, "deadline": 40, "after": ["fetch02"] },
            { "id": "parse03",   "duration": 3, "deadline": 40, "after": ["fetch03"] },
            { "id": "parse04",   "duration": 4, "deadline": 40, "after": ["fetch04"] },
            { "id": "parse05",   "duration": 6, "deadline": 45, "after": ["fetch05"] },
            { "id": "parse06",   "duration": 3, "deadline": 45, "after": ["fetch06"] },
            { "id": "parse07",   "duration": 5, "deadline": 45, "after": ["fetch07"] },
            { "id": "parse08",   "duration": 4, "deadline": 45, "after": ["fetch08"] },

            { "id": "validate01","duration": 3, "deadline": 55, "after": ["parse01"] },
            { "id": "validate02","duration": 3, "deadline": 55, "after": ["parse02"] },
            { "id": "validate03","duration": 2, "deadline": 55, "after": ["parse03"] },
            { "id": "validate04","duration": 4, "deadline": 55, "after": ["parse04"] },
            { "id": "validate05","duration": 3, "deadline": 60, "after": ["parse05"] },
            { "id": "validate06","duration": 2, "deadline": 60, "after": ["parse06"] },
            { "id": "validate07","duration": 4, "deadline": 60, "after": ["parse07"] },
            { "id": "validate08","duration": 3, "deadline": 60, "after": ["parse08"] },

            { "id": "index01",   "duration": 6, "deadline": 75, "after": ["validate01", "diskInit"] },
            { "id": "index02",   "duration": 5, "deadline": 75, "after": ["validate02", "diskInit"] },
            { "id": "index03",   "duration": 7, "deadline": 75, "after": ["validate03", "diskInit"] },
            { "id": "index04",   "duration": 4, "deadline": 75, "after": ["validate04", "diskInit"] },
            { "id": "index05",   "duration": 6, "deadline": 80, "after": ["validate05"] },
            { "id": "index06",   "duration": 5, "deadline": 80, "after": ["validate06"] },
            { "id": "index07",   "duration": 7, "deadline": 80, "after": ["validate07"] },
            { "id": "index08",   "duration": 4, "deadline": 80, "after": ["validate08"] },

            { "id": "enrich01",  "duration": 4, "deadline": 95, "after": ["index01"] },
            { "id": "enrich02",  "duration": 5, "deadline": 95, "after": ["index02"] },
            { "id": "enrich03",  "duration": 3, "deadline": 95, "after": ["index03"] },
            { "id": "enrich04",  "duration": 6, "deadline": 95, "after": ["index04"] },
            { "id": "enrich05",  "duration": 4, "deadline": 100, "after": ["index05"] },
            { "id": "enrich06",  "duration": 5, "deadline": 100, "after": ["index06"] },
            { "id": "enrich07",  "duration": 3, "deadline": 100, "after": ["index07"] },
            { "id": "enrich08",  "duration": 6, "deadline": 100, "after": ["index08"] },

            { "id": "featA",     "duration": 5, "deadline": 115, "after": ["enrich01", "enrich02"] },
            { "id": "featB",     "duration": 6, "deadline": 115, "after": ["enrich03", "enrich04"] },
            { "id": "featC",     "duration": 4, "deadline": 120, "after": ["enrich05", "enrich06"] },
            { "id": "featD",     "duration": 7, "deadline": 120, "after": ["enrich07", "enrich08"] },
            { "id": "featE",     "duration": 5, "deadline": 120, "after": ["enrich01", "enrich05"] },
            { "id": "featF",     "duration": 4, "deadline": 120, "after": ["enrich03", "enrich07"] },

            { "id": "merge01",   "duration": 5, "deadline": 135, "after": ["featA", "featB"] },
            { "id": "merge02",   "duration": 6, "deadline": 135, "after": ["featC", "featD"] },
            { "id": "merge03",   "duration": 4, "deadline": 140, "after": ["featE", "featF"] },

            { "id": "train01",   "duration": 10, "deadline": 165, "after": ["merge01"] },
            { "id": "train02",   "duration": 12, "deadline": 165, "after": ["merge02"] },
            { "id": "train03",   "duration": 9,  "deadline": 165, "after": ["merge03"] },
            { "id": "train04",   "duration": 11, "deadline": 170, "after": ["merge01", "merge02"] },

            { "id": "eval01",    "duration": 4, "deadline": 185, "after": ["train01"] },
            { "id": "eval02",    "duration": 4, "deadline": 185, "after": ["train02"] },
            { "id": "eval03",    "duration": 3, "deadline": 185, "after": ["train03"] },
            { "id": "eval04",    "duration": 5, "deadline": 190, "after": ["train04"] },

            { "id": "calib01",   "duration": 3, "deadline": 200, "after": ["eval01"] },
            { "id": "calib02",   "duration": 3, "deadline": 200, "after": ["eval02"] },
            { "id": "calib03",   "duration": 4, "deadline": 200, "after": ["eval03"] },
            { "id": "calib04",   "duration": 2, "deadline": 205, "after": ["eval04"] },

            { "id": "package01", "duration": 5, "deadline": 220, "after": ["calib01", "calib02"] },
            { "id": "package02", "duration": 6, "deadline": 220, "after": ["calib03", "calib04"] },

            { "id": "sign01",    "duration": 2, "deadline": 230, "after": ["package01"] },
            { "id": "sign02",    "duration": 2, "deadline": 230, "after": ["package02"] },

            { "id": "deploy01",  "duration": 6, "deadline": 250, "after": ["sign01"] },
            { "id": "deploy02",  "duration": 6, "deadline": 250, "after": ["sign02"] },
            { "id": "smoke01",   "duration": 3, "deadline": 260, "after": ["deploy01"] },
            { "id": "smoke02",   "duration": 3, "deadline": 260, "after": ["deploy02"] },

            { "id": "audit01",   "duration": 5, "deadline": 90 },
            { "id": "audit02",   "duration": 4, "deadline": 110 },
            { "id": "backup01",  "duration": 8, "deadline": 130 },
            { "id": "backup02",  "duration": 7, "deadline": 150 },
            { "id": "metrics01", "duration": 3, "deadline": 170 },
            { "id": "metrics02", "duration": 3, "deadline": 190 },
            { "id": "cleanup",   "duration": 4, "deadline": 270, "after": ["smoke01", "smoke02"] },
            { "id": "notify",    "duration": 2, "deadline": 275, "after": ["cleanup"] }
          ]
        }
        """;
}