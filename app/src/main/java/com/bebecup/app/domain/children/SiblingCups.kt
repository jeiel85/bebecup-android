package com.bebecup.app.domain.children

/**
 * With two or more children every world cup is one child's: siblings never
 * compete with each other, so there is no "everyone" cup. With one child (or
 * none registered) the family keeps its single cup over every saved photo.
 */
fun cupsArePerChild(childCount: Int): Boolean = childCount >= 2
