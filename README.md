# hasplib

Key control and master key system design for the campus lock shop.

The shop keeps the keying systems for every building on campus. This is the
part of that work a computer can do: hold the manufacturer specifications,
progress a bitting array, pin the cylinders, keep track of who holds what,
and work out the damage when a key goes missing.

    pip install -e ".[dev]"
    pytest

Nothing here talks to a lock. It talks about locks.
