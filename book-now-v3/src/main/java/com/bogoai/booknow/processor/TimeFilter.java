package com.bogoai.booknow.processor;

import com.bogoai.booknow.repository.BookNowRepository;

public class TimeFilter implements Runnable {

    BookNowRepository bookNowRepository;

    public TimeFilter(BookNowRepository bookNowRepository) {
        this.bookNowRepository = bookNowRepository;
    }

    @Override
    public void run() {

    }
}
