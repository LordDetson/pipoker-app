package by.babanin.pipoker.model.converter;

import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.model.TimerDto;

// The time left is counted when the timer is sent, so it reaches the page as fresh as the message itself
public class TimerToDtoConverter implements Converter<Timer, TimerDto> {

    @Override
    public TimerDto convert(MappingContext<Timer, TimerDto> context) {
        Timer timer = context.getSource();
        // A room without a timer still goes through this converter
        if(timer == null) {
            return null;
        }
        return new TimerDto(timer.getSeconds(), timer.remaining().toMillis());
    }
}
