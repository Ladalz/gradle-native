package dev.nokee.platform.base.internal

import dev.nokee.platform.base.Binary
import org.gradle.api.provider.Provider
import spock.lang.Subject

@Subject(DefaultBinaryView)
class DefaultBinaryViewTest extends AbstractViewTest<Binary> {
	final def backingCollection = objects.domainObjectSet(TestBinary)

	def getBackingCollection() {
		return backingCollection
	}

	@Override
	void realizeBackingCollection() {
		backingCollection.iterator().next()
	}

	def createView() {
		return objects.newInstance(DefaultBinaryView, Binary.class, backingCollection, realizeTrigger)
	}

	@Override
	Provider<Binary> getA() {
		return providers.provider { new TestBinary('a') }
	}

	@Override
	Provider<Binary> getB() {
		return providers.provider { new TestBinary('b') }
	}

	@Override
	Provider<Binary> getC() {
		return providers.provider { new TestChildBinary('c') }
	}

	@Override
	Class<TestBinary> getType() {
		return TestBinary
	}

	@Override
	Class<TestChildBinary> getOtherType() {
		return TestChildBinary
	}

	@Override
	void addToBackingCollection(Provider<Binary> v) {
		backingCollection.addLater(v)
	}

	static class TestBinary implements Binary, AbstractViewTest.Identifiable {
		private final String identification

		TestBinary(String identification) {
			this.identification = identification
		}

		@Override
		String getIdentification() {
			return identification
		}
	}

	static class TestChildBinary extends TestBinary {
		TestChildBinary(String identification) {
			super(identification)
		}
	}
}
