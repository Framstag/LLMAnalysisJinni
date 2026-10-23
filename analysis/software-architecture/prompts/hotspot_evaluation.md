## Current Goal

* We want to know which parts of this project are both complicated and alive: the classes whose file changes often and that carry a high complexity.
* Report the counted hotspots of the project with the two figures the join holds beside every class, and state the limits of the join.
* A hotspot is not a verdict. The analysis reads the counted figures and says what they mean.

## Solution Strategy

* Call **`java_get_hotspots`** once and read the counted result it returns. The tool counts, you judge.
* The tool joins the counted version history of the project with the counted metrics of the raw Java module reports. It returns, per ranked class, the class name, the modules the class appears in, the source file, the change count of the file, the added and the removed lines, the counted cyclomatic complexity of the class, the number of its methods and the rank.
* A class is reported once with the modules it appears in: an aggregator module holds the same classes as its inner modules, so the class appears in the report of several modules. Report the class once, name all the modules the tool named for it, and report the highest complexity of its appearances, as the tool returned it.
* A module that the tool names as skipped carries no evidence. Do not invent a hotspot for it, and say in the reasoning that the module was skipped.
* Keep the order the tool returned, and report the two figures of every class beside its rank.

### The two figures of a hotspot

* A hotspot is a class that is both complicated and alive. The two figures the join holds are the change count of the file the class was declared in, and the counted cyclomatic complexity of the class.
* Neither figure alone makes a hotspot. A class that changes often but is simple is not a hotspot, and a class that is complicated but whose file never changed is not a hotspot either. The tool leaves such a class out of the ranking, and you do not add it.
* The change count is the number of commits that changed the file of the class. The complexity is the counted cyclomatic complexity of the methods of the class, as the raw Java module report counted it.

### The rank

* The rank is the position of a class among the classes of this project that changed. It is a rank among the classes of this project, not a score, not a threshold and not a verdict.
* Never state a threshold that would decide the same outcome in another project: the same figures in another project lead to another order.
* Report both figures beside every rank, so that a reader who weighs them differently can reorder the list without counting anything again.
* Report at most 10 hotspots, the ones the tool ranked first.

### The history that is absent

* When the tool reports that the counted history of the project is absent, the absence and its reason are the result of the analysis, not an empty list of hotspots. The project holds no counted hotspots, which is not the same as a project without hotspots.
* Report the absence in the `history` object of the answer: `available` is false, `absence` holds the case the tool named, and `reason` holds the reason the tool gave.
* The `hotspots` list is then empty, and the `reasoning` states that the project holds no counted hotspots because its counted history is absent.

### The history that is truncated

* When the tool reports that the counted history is truncated, the hotspots of the counted window are the result, and the truncation is named in the `history` object: `truncated` is true and the `statement` names the truncation and the classes that are left out.
* The classes that were changed before the counted window are not part of the result. Do not say that the project holds no such classes, say that the counted window does not reach them.

### The limits of the analysis

* The change frequency counts the commits of the counted window of the history. It is bound to that window, and the window the tool reported is the `window` of the `history` object.
* A class carries the change count of its file. A file that holds a class and a lot of other code gives that class the change count of the file, and a class that is changed together with another class of the same file changes with it.
* A truncated history leaves the classes that were changed before the counted window out.
* No ownership and no age of the code are part of this analysis. The counted history holds the authors, but the hotspot does not rank by author, and the age of a class is not counted at all.
* A class that was moved between files carries the history of its new file, because the join follows the file the class is declared in now.

### The limits are part of the answer

* The `limits` array of the answer holds the limits of the join, one entry per limit: the counted window as the bound of the change frequency, the change count of the file of a class, the classes a truncated history leaves out, and the absence of ownership and of the age of the code.
* The `reasoning` of the answer says what the counted figures show and what they cannot show.

## Hints

* The counted evidence is written as the CSV report "Hotspots/Hotspots.csv" of the workspace, one row per ranked class. Use the values the tool reported and name a class and its modules as the tool spelled them.
* The `history.statement` holds the statement of the history the analysis rests on: the counted window, and the truncation when the history is truncated.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `HotspotEvaluation` response schema.
* Each `hotspots[]` entry MUST be a JSON object with the fields `className`, `modules`, `rank`, `changeCount`, `complexity`, `methodCount` and `reasoning`. Do NOT use plain strings.
* The `modules` field of a hotspot entry is an array of strings, one entry per module whose report contains the class. A class of a single module holds that one module. Every entry is a module name exactly as the tool spelled it, nothing else: no sentence, no reason, no tool output, no explanation. Anything else in this field is a defect of the answer and the answer is rejected, because the field is bounded.
* The `history` object MUST be a JSON object with the fields `available`, `window`, `truncated`, `absence`, `reason` and `statement`.
* The `limits` array holds one string per limit of the join.
* Report at most 10 hotspots. A longer response is cut off before its JSON is complete, so it is rejected and retried.
* You must only return clean JSON content without any prefix or postfix.
