package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;

import java.util.Objects;
import java.util.function.Function;

class ProviderViewBuilder {

    private final LimitedTextProcessor textProcessor;
    private final LimitedTextProcessor textProcessorWithImage;

    private String image = null;
    private boolean imageSet = false;

    public ProviderViewBuilder(
            final String delimiter,
            final Integer limit,
            final Integer limitWithImage
    ) {
        this.textProcessor = new LimitedTextProcessor(new LimitedTextBuilder(delimiter, limit));
        this.textProcessorWithImage = new LimitedTextProcessor(new LimitedTextBuilder(delimiter, limitWithImage));
    }

    public boolean append(
            final Function<Integer, String> text,
            final String image
    ) {
        boolean result = textProcessor.accept(text);
        if (textProcessorWithImage.accept(text)) {
            result = true;
            updateImage(image);
        }
        return result;
    }

    private void updateImage(final String image) {
        if (imageSet) {
            if ((image != null) && !Objects.equals(image, this.image)) {
                this.image = null;
            }
        } else {
            if (image != null) {
                this.image = image;
                imageSet = true;
            }
        }
    }

    public ProviderView build() {
        final LimitedTextProcessor bestTextProcessor = textProcessor.getProcessed() > textProcessorWithImage.getProcessed()
                ? textProcessor
                : textProcessorWithImage;
        return new ProviderView(
                image,
                bestTextProcessor.getText()
        );
    }

    private static class LimitedTextProcessor {

        private final LimitedTextBuilder viewBuilder;
        private int processed = 0;
        private boolean canAccept = true;

        public LimitedTextProcessor(final LimitedTextBuilder viewBuilder) {
            this.viewBuilder = viewBuilder;
        }

        public boolean accept(final Function<Integer, String> view) {
            if (canAccept && viewBuilder.append(view)) {
                processed++;
                return true;
            } else {
                canAccept = false;
                return false;
            }
        }

        public int getProcessed() {
            return processed;
        }

        public String getText() {
            return viewBuilder.build();
        }

    }

    private static class LimitedTextBuilder {

        private final String delimiter;

        private Integer limit;
        private String text = null;
        private boolean canAppend = true;

        public LimitedTextBuilder(
                final String delimiter,
                final Integer limit
        ) {
            this.delimiter = delimiter;
            this.limit = limit;
        }

        public boolean append(final Function<Integer, String> limitable) {
            if (!canAppend) {
                return false;
            }

            final String limited = limitable.apply(this.limit);
            if (limited == null) {
                canAppend = false;
                return false;
            }

            final String appendable = this.text == null ? limited : delimiter + limited;
            if ((this.limit != null) && (appendable.length() > this.limit)) {
                canAppend = false;
                return false;
            }

            this.limit = this.limit == null ? null : this.limit - appendable.length();
            this.text = this.text == null ? appendable : this.text + appendable;
            return true;
        }

        public String build() {
            return text;
        }

    }

}
