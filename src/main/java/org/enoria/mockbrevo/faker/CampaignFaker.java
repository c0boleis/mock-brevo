package org.enoria.mockbrevo.faker;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.enoria.mockbrevo.domain.Account;
import org.enoria.mockbrevo.domain.EmailCampaign;
import org.enoria.mockbrevo.domain.EmailCampaignRepository;
import org.enoria.mockbrevo.util.MockData;
import org.springframework.stereotype.Service;

@Service
public class CampaignFaker {

    private static final String[] NAME_POOL = {
            "Newsletter de bienvenue",
            "Lettre mensuelle",
            "Campagne de remerciement",
            "Appel aux dons printemps",
            "Récap trimestriel",
            "Relance donateurs",
            "Annonce événement",
            "Offre spéciale adhérents"
    };

    private static final String[] SUBJECT_POOL = {
            "Bienvenue chez nous",
            "Vos dernières actualités",
            "Un grand merci !",
            "Votre impact en chiffres",
            "On compte sur vous",
            "Découvrez nos nouveautés",
            "C'est bientôt la fin…",
            "Votre rapport annuel"
    };

    private static final String[] UTM_POOL = {
            "welcome", "monthly", "quarterly", "thank-you", "spring-campaign", "reminder"
    };

    private final EmailCampaignRepository campaigns;

    public CampaignFaker(EmailCampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    public EmailCampaign create(Account account, Spec spec) {
        Spec s = spec == null ? new Spec(null, null, null, null, null, null, null) : spec;
        Random r = MockData.seededFrom(account.getApiKey(), "faker-campaign", System.nanoTime());

        EmailCampaign c = new EmailCampaign();
        c.setAccount(account);
        c.setName(s.name != null && !s.name.isBlank() ? s.name : MockData.pick(r, NAME_POOL));
        c.setSubject(s.subject != null && !s.subject.isBlank() ? s.subject : MockData.pick(r, SUBJECT_POOL));
        c.setSenderName(s.senderName != null ? s.senderName : (account.getFirstName() + " " + account.getLastName()));
        c.setSenderEmail(s.senderEmail != null ? s.senderEmail : account.getEmail());
        c.setReplyTo(c.getSenderEmail());
        c.setTemplateId(null);
        c.setUtmCampaign(s.utmCampaign != null ? s.utmCampaign : MockData.pick(r, UTM_POOL));
        c.setRecipientListIdsCsv(s.listIdsCsv);
        c.setParamsJson(null);

        String status = s.status != null ? s.status.toLowerCase() : "sent";
        c.setStatus(status);

        Instant now = Instant.now();
        c.setCreatedAt(now);
        if ("sent".equals(status)) {
            c.setSentDate(now);
            c.setDeliveredCount(MockData.bounded(r, 200, 5000));
        }
        return campaigns.save(c);
    }

    public List<EmailCampaign> seedDefaults(Account account) {
        return List.of(
                create(account, new Spec("Newsletter de bienvenue", "Bienvenue chez nous", null, null, "welcome", null, "sent")),
                create(account, new Spec("Lettre mensuelle", "Vos dernières actualités", null, null, "monthly", null, "sent"))
        );
    }

    public record Spec(
            String name,
            String subject,
            String senderName,
            String senderEmail,
            String utmCampaign,
            String listIdsCsv,
            String status) {}
}
