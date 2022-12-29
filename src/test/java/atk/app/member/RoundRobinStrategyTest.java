package atk.app.member;

import static org.assertj.core.api.Assertions.assertThat;
import atk.app.util.MemberStateUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundRobinStrategyTest {

    @Test
    void roundRobinStrategyShouldReturnAllElementsInTheList() {
        var listOfMembers = new ArrayList<>(List.of(MemberStateUtil.aliveMember(), MemberStateUtil.aliveMember()));
        var roundRobinStrategy = new MemberList.RoundRobinStrategy(listOfMembers);

        //when next member is called more than number of elements
        var result = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            result.add(roundRobinStrategy.nextMember());
        }

        //then
        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyInAnyOrder(listOfMembers.get(0), listOfMembers.get(1));
    }
}
