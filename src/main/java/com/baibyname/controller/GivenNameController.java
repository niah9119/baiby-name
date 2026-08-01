package com.baibyname.controller;

import com.baibyname.exception.NameNotFoundException;
import com.baibyname.service.GivenNameService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;

import java.util.Optional;

/**
 * Controller for given name landing pages.
 * Each name has a dedicated page with trend data, bearers, and style attributes.
 */
@Controller
@RequestMapping("/names")
public class GivenNameController {

    private final GivenNameService givenNameService;

    public GivenNameController(GivenNameService givenNameService) {
        this.givenNameService = givenNameService;
    }

    /**
     * Display landing page for a given name.
     *
     * @param name the name to display (URL-encoded, supports non-ASCII)
     * @param model the Thymeleaf model
     * @return view name for name landing page
     */
    @GetMapping("/{name}")
    public String getByName(
            @PathVariable("name") String name,
            Model model) {

        Optional<GivenNameService.NameDetails> details = givenNameService.getByName(name);

        if (details.isEmpty()) {
            throw new NameNotFoundException(name);
        }

        model.addAttribute("nameDetails", details.get());
        model.addAttribute("similarNames", givenNameService.findSimilarNames(details.get()));
        model.addAttribute("famousBearers", details.get().famousBearers());

        return "name";
    }
}
