/*
 * Copyright 2014 B.Schmersow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zee.audiobenchmark.datatypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class that provides menu list entries.
 */
public class EntryLibTest {

	/**
	 * Array of items.
	 */
	public static List<ListMenuEntry> ITEMS = new ArrayList<ListMenuEntry>();

	/**
	 * The map of items, by ID.
	 */
	public static Map<String, ListMenuEntry> ITEM_MAP = new HashMap<String, ListMenuEntry>();

	static {
		// Add items.
		addItem(new ListMenuEntry("1", "Overview / Settings"));
		addItem(new ListMenuEntry("2", "Audio Track"));
		addItem(new ListMenuEntry("3", "Open SL"));
	}

	private static void addItem(ListMenuEntry item) {
		ITEMS.add(item);
		ITEM_MAP.put(item.id, item);
	}

	/**
	 * A list menu entry.
	 */
	public static class ListMenuEntry {
		public String id;
		public String content;

		public ListMenuEntry(String id, String content) {
			this.id = id;
			this.content = content;
		}

		@Override
		public String toString() {
			return content;
		}
	}
}
