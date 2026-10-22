package com.framstag.llmaj.display;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The formatting of the numbers the display renders.
 */
public class AnsiTest {

    @Test
    public void aCountBelowAThousandIsShownAsItIs() {
        assertEquals("0", Ansi.formatTokenCount(0));
        assertEquals("999", Ansi.formatTokenCount(999));
    }

    @Test
    public void aBiggerCountIsShownInThousands() {
        assertEquals("1.0K", Ansi.formatTokenCount(1000));
        assertEquals("1.2K", Ansi.formatTokenCount(1234));
        assertEquals("21.0K", Ansi.formatTokenCount(21001));
    }

    @Test
    public void aCountThatIsNotReportedIsShownAsADash() {
        assertEquals("-", Ansi.formatTokenCount(null),
                "a count the provider did not report is not a count of zero");
    }

    @Test
    public void theFormattingDoesNotDependOnTheLocaleOfTheMachine() {
        Locale original = Locale.getDefault();

        try {
            // A locale that writes the decimal separator as a comma.
            Locale.setDefault(Locale.GERMANY);

            assertEquals("1.2K", Ansi.formatTokenCount(1234),
                    "the count must not be rendered with the decimal separator of the machine");
        } finally {
            Locale.setDefault(original);
        }
    }
}
