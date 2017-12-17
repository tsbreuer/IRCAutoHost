import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class Test {
	public static void main(String[] args) {
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			list.add(i);
		}

		try {
			for (Integer i : list) {
				list.remove(3);
			}
		} catch (Exception e) {
			System.out.println("Can't modify from for loop");
		}

		try {
			Iterator iter = list.iterator();
			while (iter.hasNext()) {
				list.remove(5);
			}
		} catch (Exception e) {
			System.out.println("Can't modify from iterator either.");
		}

		try {
			Iterator iter = list.iterator();
			while (iter.hasNext()) {
				if (iter.next().equals(5)) {
					iter.remove();
				}
			}
			System.out.println("But you can modify using methods on the Iterator.");
		} catch (Exception e) {
			System.out.println("Can't modify from iterator either.");
		}
	}
}
