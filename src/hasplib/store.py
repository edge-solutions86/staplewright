"""A keyed collection, written once instead of six times.

Half the classes in this library are the same shape. A dictionary keyed by
some identifier, a list holding the order things arrived in, a lookup that
raises a readable not found, and an add that refuses a duplicate. They had
each grown their own copy and the copies had started to differ: two of them
iterated in insertion order and two in key order, which is exactly the kind
of thing that makes a report come out in a different order depending on which
table it came from.

So the collection lives here. Callers keep their own vocabulary on top of it
and only the storage is shared, because the vocabulary is the part that is
worth reading.
"""

from __future__ import annotations

from collections.abc import Callable, Iterator
from typing import Generic, TypeVar

from hasplib.errors import ConflictError, NotFoundError

T = TypeVar("T")


class Registry(Generic[T]):
    """Items under string keys, in the order they were added.

    ``noun`` and ``key_name`` are only there so the errors read like the
    thing being stored. A missing cylinder should say cylinder and name the
    serial, not talk about keys and items.
    """

    def __init__(self, noun: str, key_name: str = "id") -> None:
        self.noun = noun
        self.key_name = key_name
        self._items: dict[str, T] = {}
        self._order: list[str] = []

    def add(self, key: str, item: T) -> T:
        """Store something new, refusing a key that is already taken."""
        if key in self._items:
            raise ConflictError(f"that {self.noun} is already here", **{self.key_name: key})
        self._items[key] = item
        self._order.append(key)
        return item

    def put(self, key: str, item: T) -> T:
        """Write over whatever is there, keeping the arrival order."""
        if key not in self._items:
            self._order.append(key)
        self._items[key] = item
        return item

    def get(self, key: str) -> T:
        try:
            return self._items[key]
        except KeyError:
            raise NotFoundError(f"no such {self.noun}", **{self.key_name: key}) from None

    def find(self, key: str) -> T | None:
        """The same lookup for callers that treat missing as an answer."""
        return self._items.get(key)

    def remove(self, key: str) -> T:
        item = self.get(key)
        del self._items[key]
        self._order.remove(key)
        return item

    def has(self, key: str) -> bool:
        return key in self._items

    def keys(self) -> tuple[str, ...]:
        """The keys in arrival order."""
        return tuple(self._order)

    def sorted_keys(self) -> tuple[str, ...]:
        return tuple(sorted(self._items))

    def values(self) -> tuple[T, ...]:
        """Everything in arrival order."""
        return tuple(self._items[key] for key in self._order)

    def by_key(self) -> tuple[T, ...]:
        """Everything in key order, which is what most reports print."""
        return tuple(self._items[key] for key in sorted(self._items))

    def where(self, matches: Callable[[T], bool]) -> tuple[T, ...]:
        """Everything that passes a test, still in arrival order."""
        return tuple(item for item in self.values() if matches(item))

    def first(self, matches: Callable[[T], bool]) -> T | None:
        for item in self.values():
            if matches(item):
                return item
        return None

    def __contains__(self, key: object) -> bool:
        return key in self._items

    def __iter__(self) -> Iterator[T]:
        return iter(self.values())

    def __len__(self) -> int:
        return len(self._items)
