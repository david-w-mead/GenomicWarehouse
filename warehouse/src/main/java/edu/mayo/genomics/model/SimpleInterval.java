package edu.mayo.genomics.model;

import edu.mayo.genomics.utils.hellbender.UserException;
import edu.mayo.genomics.utils.hellbender.Utils;
import htsjdk.samtools.util.Locatable;

import java.io.Serializable;

/**
 * Minimal immutable class representing a 1-based closed ended genomic interval
 * SimpleInterval does not allow null contig names.  It cannot represent an unmapped Locatable.
 *
 *@warning 0 length intervals are NOT currently allowed, but support may be added in the future
 */
public class SimpleInterval implements Locatable, Serializable {

    private static final long serialVersionUID = 1L;
    public static final char CONTIG_SEPARATOR = ':';
    public static final char START_END_SEPARATOR = '-';
    public static final String END_OF_CONTIG = "+"; //note: needs to be a String because it's used in an endsWith call.

    protected int start;
    protected int end;
    protected String contig;


    /**
     * Create a new immutable 1-based interval of the form [start, end]
     * @param contig the name of the contig, must not be null
     * @param start  1-based inclusive start position
     * @param end  1-based inclusive end position
     */
    public SimpleInterval(final String contig, final int start, final int end){
        validatePositions(contig, start, end);
        this.contig = contig;
        this.start = start;
        this.end = end;
    }

    /**
     * Create a new SimpleInterval from a {@link Locatable}
     * @param locatable any Locatable
     * @throws IllegalArgumentException if locatable violates any of the SimpleInterval constraints or is null
     */
    public SimpleInterval(final Locatable locatable){
        this(Utils.nonNull(locatable).getContig(),
                locatable.getStart(), locatable.getEnd());
    }

    public SimpleInterval() {
    }

    /**
     * Test that these are valid values for constructing a SimpleInterval
     * @throws IllegalArgumentException if it is invalid
     */
    private static void validatePositions(final String contig, final int start, final int end) {
        if ( contig == null ) {
            throw new IllegalArgumentException("contig cannot be null");
        }
        if ( start <= 0 ) {
            throw new IllegalArgumentException(String.format("SimpleInterval is 1 based, so start must be >= 1, start: %d", start));
        }
        if ( end < start ) {
            throw new IllegalArgumentException(String.format("end must be >= start. start:%d end:%d", start, end));
        }
    }

    /**
     * Makes an interval by parsing the string.
     *
     * @warning this method does not fill in the true contig end values
     * for intervals that reach to the end of their contig,
     * uses {@link Integer#MAX_VALUE} instead.
     *
     * Semantics of start and end are defined in {@link Locatable}.
     * The format is one of:
     *
     * contig           (Represents the whole contig, from position 1 to the {@link Integer#MAX_VALUE})
     *
     * contig:start     (Represents the 1-element range start-start on the given contig)
     *
     * contig:start-end (Represents the range start-end on the given contig)
     *
     * contig:start+    (Represents the prefix of the contig starting at the given start position and ending at {@link Integer#MAX_VALUE})
     *
     * examples (note that _all_ commas in numbers are simply ignored, for human convenience):
     *
     * 'chr2', 'chr2:1000000' or 'chr2:1,000,000-2,000,000' or 'chr2:1000000+'
     */
    public SimpleInterval(final String str){
        /* Note: we want to keep the class immutable. So all fields need to be final.
         * But only constructors can assign to final fields.
         * So we can either keep this parsing code in the constructor or make a static factory method
         * and make multiple objects. We chose the former.
         */
        final String contig;
        final int start;
        final int end;

        final int colonIndex = str.lastIndexOf(CONTIG_SEPARATOR);
        if (colonIndex == -1) {
            contig = str;  // chr1
            start = 1;
            end = Integer.MAX_VALUE;
        } else {
            contig = str.substring(0, colonIndex);
            final int dashIndex = str.indexOf(START_END_SEPARATOR, colonIndex);
            if(dashIndex == -1) {
                if(str.endsWith(END_OF_CONTIG)) {
                    start = parsePosition(str.substring(colonIndex + 1, str.length() - 1));  // chr:1+
                    end = Integer.MAX_VALUE;
                } else {
                    start = parsePosition(str.substring(colonIndex + 1));   // chr1:1
                    end = start;
                }
            } else {
                start = parsePosition(str.substring(colonIndex + 1, dashIndex));  // chr1:1-1
                end = parsePosition(str.substring(dashIndex + 1));
            }
        }

        validatePositions(contig, start, end);
        this.contig = contig;
        this.start = start;
        this.end = end;
    }

    /**
     * Parses a number like 100000 or 1,000,000 into an int.
     */
    private static int parsePosition(final String pos) {
        try {
            return Integer.parseInt(pos.replaceAll(",", "")); //strip commas
        } catch (NumberFormatException e){
            throw new UserException("Problem parsing start/end value in interval string. Value was: " + pos, e);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SimpleInterval that = (SimpleInterval) o;

        if (end != that.end) return false;
        if (start != that.start) return false;
        return contig.equals(that.contig);

    }

    @Override
    public int hashCode() {
        int result = start;
        result = 31 * result + end;
        result = 31 * result + contig.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s:%s-%s", contig, start, end);
    }

    /**
     * @return name of the contig this is mapped to
     */
    public final String getContig(){
        return contig;
    }

    /** Gets the 1-based start position of the interval on the contig. */
    public final int getStart(){
        return start;
    }

    /**
     * @return the 0-based start position (from the GA4GH spec).
     */
    public final long getGA4GHStart() {return start - 1; }

    /**
     * @return the 1-based closed-ended end position of the interval on the contig.
     */
    public final int getEnd(){
        return end;
    }

    /**
     * @return the typical end spans are [zero-start,end) (from the GA4GH spec).
     */
    public final long getGA4GHEnd() { return end; }

    /**
     * @return number of bases covered by this interval (will always be > 0)
     */
    public final int size() {
        return end - start + 1;
    }

    /**
     * Determines whether this interval overlaps the provided locatable.
     *
     * @param other interval to check
     * @return true if this interval overlaps other, otherwise false
     */
    public final boolean overlaps( final Locatable other ) {
        if ( other == null || other.getContig() == null ) {
            return false;
        }

        return this.contig.equals(other.getContig()) && this.start <= other.getEnd() && other.getStart() <= this.end;
    }

    /**
     * Determines whether this interval comes within "margin" of overlapping the provided locatable.
     * This is the same as plain overlaps if margin=0.
     *
     * @param other interval to check
     * @param margin how many bases may be between the two interval for us to still consider them overlapping.
     * @return true if this interval overlaps other, otherwise false
     */
    public final boolean overlapsWithMargin(final Locatable other, int margin) {
        if ( other == null || other.getContig() == null ) {
            return false;
        }

        return this.contig.equals(other.getContig()) && this.start <= other.getEnd() + margin && other.getStart() - margin <= this.end;
    }


    /**
     * Determines whether this interval contains the entire region represented by other
     * (in other words, whether it covers it).
     *
     * @param other interval to check
     * @return true if this interval contains all of the bases spanned by other, otherwise false
     */
    public final boolean contains( final Locatable other ) {
        if ( other == null || other.getContig() == null ) {
            return false;
        }

        return this.contig.equals(other.getContig()) && this.start <= other.getStart() && this.end >= other.getEnd();
    }

    /**
     * getSpanningInterval returns interval that covers all of the locations passd in.
     * @param locations the locations to be spanned (on a single contig)
     * @return the minimal span that covers all locations (could be null if no locations are passed in).
     */
    public static SimpleInterval getSpanningInterval(Iterable<? extends Locatable> locations) {
        int min = Integer.MAX_VALUE;
        int max = 1;
        String contig = null;
        for (Locatable l : locations) {
            if (contig == null) {
                contig = l.getContig();
            } else if (!l.getContig().equals(contig)) {
                throw new IllegalArgumentException("found different contigs from inputs: " + contig + ","
                        + l.getContig());
            }

            if (l.getStart() < min) {
                min = l.getStart();
            }
            if (l.getEnd() > max) {
                max = l.getEnd();
            }
        }
        if (contig == null) {
            return null;
        }
        return new SimpleInterval(contig, min, max);
    }
}
