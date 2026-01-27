package mediaservice.mappers;

import mediaservice.models.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    // Helper mapper for Account references in other mappers
}

