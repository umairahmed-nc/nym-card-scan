package com.nymcard.cardsscan.ml

import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.FindFourModel
import java.util.Collections

class PostDetectionAlgorithm internal constructor(
    boxes: MutableList<DetectedBox>,
    findFour: FindFourModel
) {
    private val kDeltaRowForCombine = 2
    private val kDeltaColForCombine = 2
    private val numRows: Int
    private val numCols: Int
    private val sortedBoxes: ArrayList<DetectedBox>

    init {
        this.numCols = FindFourModel.Companion.cols
        this.numRows = FindFourModel.Companion.rows

        this.sortedBoxes = ArrayList<DetectedBox>()
        boxes.sort<DetectedBox>()
        boxes.reverse()
        for (box in boxes) {
            val kMaxBoxesToDetect = 20
            if (this.sortedBoxes.size >= kMaxBoxesToDetect) {
                break
            }
            this.sortedBoxes.add(box)
        }
    }

    fun horizontalNumbers(): ArrayList<ArrayList<DetectedBox?>?> {
        val boxes = this.combineCloseBoxes(
            kDeltaRowForCombine,
            kDeltaColForCombine
        )
        val kNumberWordCount = 4
        val lines = this.findHorizontalNumbers(boxes, kNumberWordCount)

        val linesOut = ArrayList<ArrayList<DetectedBox?>?>()
        // boxes should be roughly evenly spaced, reject any that aren't
        for (line in lines) {
            val deltas = ArrayList<Int>()
            for (idx in 0..<(line.size - 1)) {
                deltas.add(line[idx + 1]!!.col - line.get(idx)!!.col)
            }

            Collections.sort<Int>(deltas)
            val maxDelta: Int = deltas[deltas.size - 1]
            val minDelta: Int = deltas[0]

            if ((maxDelta - minDelta) <= 2) {
                linesOut.add(line)
            }
        }

        return linesOut
    }

    fun verticalNumbers(): ArrayList<ArrayList<DetectedBox?>?> {
        val boxes = this.combineCloseBoxes(
            kDeltaRowForCombine,
            kDeltaColForCombine
        )
        val lines = this.findVerticalNumbers(boxes)

        val linesOut = ArrayList<ArrayList<DetectedBox?>?>()
        // boxes should be roughly evenly spaced, reject any that aren't
        for (line in lines) {
            val deltas = ArrayList<Int>()
            for (idx in 0..<(line.size - 1)) {
                deltas.add(line[idx + 1]!!.row - line[idx]!!.row)
            }

            Collections.sort(deltas)
            val maxDelta: Int = deltas[deltas.size - 1]
            val minDelta: Int = deltas[0]

            if ((maxDelta - minDelta) <= 2) {
                linesOut.add(line)
            }
        }

        return linesOut
    }

    private fun horizontalPredicate(currentWord: DetectedBox, nextWord: DetectedBox): Boolean {
        val kDeltaRowForHorizontalNumbers = 1
        val deltaRow = kDeltaRowForHorizontalNumbers
        return nextWord.col > currentWord.col && nextWord.row >= (currentWord.row - deltaRow) && nextWord.row <= (currentWord.row + deltaRow)
    }

    private fun verticalPredicate(currentWord: DetectedBox, nextWord: DetectedBox): Boolean {
        val kDeltaColForVerticalNumbers = 1
        val deltaCol = kDeltaColForVerticalNumbers
        return nextWord.row > currentWord.row && nextWord.col >= (currentWord.col - deltaCol) && nextWord.col <= (currentWord.col + deltaCol)
    }

    private fun findNumbers(
        currentLine: ArrayList<DetectedBox?>, words: ArrayList<DetectedBox>,
        useHorizontalPredicate: Boolean, numberOfBoxes: Int,
        lines: ArrayList<ArrayList<DetectedBox?>>
    ) {
        if (currentLine.size == numberOfBoxes) {
            lines.add(currentLine)
            return
        }

        if (words.size == 0) {
            return
        }

        val currentWord = currentLine.get(currentLine.size - 1)
        if (currentWord == null) {
            return
        }


        for (idx in words.indices) {
            val word = words.get(idx)
            if (useHorizontalPredicate && horizontalPredicate(currentWord, word)) {
                val newCurrentLine = ArrayList<DetectedBox?>(currentLine)
                newCurrentLine.add(word)
                findNumbers(
                    newCurrentLine, dropFirst(words, idx + 1), useHorizontalPredicate,
                    numberOfBoxes, lines
                )
            } else if (verticalPredicate(currentWord, word)) {
                val newCurrentLine = ArrayList<DetectedBox?>(currentLine)
                newCurrentLine.add(word)
                findNumbers(
                    newCurrentLine, dropFirst(words, idx + 1), useHorizontalPredicate,
                    numberOfBoxes, lines
                )
            }
        }
    }

    private fun dropFirst(boxes: ArrayList<DetectedBox>, n: Int): ArrayList<DetectedBox> {
        val result = ArrayList<DetectedBox>()
        for (idx in n..<boxes.size) {
            result.add(boxes.get(idx))
        }
        return result
    }

    private fun findHorizontalNumbers(
        words: ArrayList<DetectedBox>,
        numberOfBoxes: Int
    ): ArrayList<ArrayList<DetectedBox?>> {
        Collections.sort<DetectedBox?>(words, colCompare)
        val lines = ArrayList<ArrayList<DetectedBox?>>()
        for (idx in words.indices) {
            val currentLine = ArrayList<DetectedBox?>()
            currentLine.add(words.get(idx))
            findNumbers(
                currentLine, dropFirst(words, idx + 1), true,
                numberOfBoxes, lines
            )
        }

        return lines
    }

    private fun findVerticalNumbers(words: ArrayList<DetectedBox>): ArrayList<ArrayList<DetectedBox?>> {
        val numberOfBoxes = 4
        Collections.sort<DetectedBox?>(words, rowCompare)
        val lines = ArrayList<ArrayList<DetectedBox?>>()
        for (idx in words.indices) {
            val currentLine = ArrayList<DetectedBox?>()
            currentLine.add(words.get(idx))
            findNumbers(
                currentLine, dropFirst(words, idx + 1), false,
                numberOfBoxes, lines
            )
        }

        return lines
    }

    private fun combineCloseBoxes(deltaRow: Int, deltaCol: Int): ArrayList<DetectedBox> {
        val cardGrid = Array<BooleanArray?>(this.numRows) { BooleanArray(this.numCols) }
        for (row in 0..<this.numRows) {
            for (col in 0..<this.numCols) {
                cardGrid[row]!![col] = false
            }
        }

        for (box in this.sortedBoxes) {
            cardGrid[box.row]!![box.col] = true
        }

        for (box in this.sortedBoxes) {
            if (!cardGrid[box.row]!![box.col]) {
                continue
            }
            for (row in (box.row - deltaRow)..(box.row + deltaRow)) {
                for (col in (box.col - deltaCol)..(box.col + deltaCol)) {
                    if (row >= 0 && row < this.numRows && col >= 0 && col < this.numCols) {
                        cardGrid[row]!![col] = false
                    }
                }
            }

            // add this box back
            cardGrid[box.row]!![box.col] = true
        }

        val combinedBoxes = ArrayList<DetectedBox>()
        for (box in this.sortedBoxes) {
            if (cardGrid[box.row]!![box.col]) {
                combinedBoxes.add(box)
            }
        }

        return combinedBoxes
    }

    companion object {
        private val colCompare: Comparator<DetectedBox> =
            Comparator<DetectedBox> { o1, o2 -> if (o1.col < o2.col) -1 else (if (o1.col == o2.col) 0 else 1) }
        private val rowCompare: Comparator<DetectedBox> =
            Comparator<DetectedBox> { o1, o2 -> if (o1.row < o2.row) -1 else (if (o1.row == o2.row) 0 else 1) }
    }
}
