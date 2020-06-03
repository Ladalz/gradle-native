package dev.nokee.platform.base;

import org.gradle.api.Action;

/**
 * A view of the binaries that are created and configured as they are required.
 *
 * @param <T> type of the elements in this view
 * @since 0.3
 */
public interface BinaryView<T extends Binary> extends View<T> {
	/**
	 * Returns a binary view containing the objects in this collection of the given type.
	 * The returned collection is live, so that when matching objects are later added to this collection, they are also visible in the filtered binary view.
	 *
	 * @param type The type of binary to find.
	 * @param <S> The base type of the new binary view.
	 * @return The matching binaries. Returns an empty collection if there are no such objects in this collection.
	 */
	<S extends T> BinaryView<S> withType(Class<S> type);
}
